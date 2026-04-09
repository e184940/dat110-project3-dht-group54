package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

public class MutualExclusion {

	private static final Logger logger = LogManager.getLogger(MutualExclusion.class);

	private boolean CS_BUSY = false;
	private boolean WANTS_TO_ENTER_CS = false;
	private List<Message> queueack;
	private List<Message> mutexqueue;

	private LamportClock clock;
	private Node node;

	public MutualExclusion(Node node) throws RemoteException {
		this.node = node;
		clock = new LamportClock();
		queueack = new ArrayList<Message>();
		mutexqueue = new ArrayList<Message>();
	}

	public synchronized void acquireLock() {
		CS_BUSY = true;
	}

	public void releaseLocks() {
		WANTS_TO_ENTER_CS = false;
		CS_BUSY = false;
	}

	public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {

		logger.info(node.nodename + " wants to access CS");

		queueack.clear();
		mutexqueue.clear();

		clock.increment();
		message.setClock(clock.getClock());

		WANTS_TO_ENTER_CS = true;

		List<Message> activenodes = removeDuplicatePeersBeforeVoting();
		multicastMessage(message, activenodes);

		// Venteloggikk: Sjekker om vi har fått nok stemmer
		if (areAllMessagesReturned(activenodes.size())) {

			acquireLock();
			node.broadcastUpdatetoPeers(updates);

			// Slipp låsen og varsle de som står i kø
			releaseLocks();
			multicastReleaseLocks(new HashSet<>(activenodes));
			return true;
		}

		return false;
	}

	// 1. Multicast melding til alle noder, inkludert seg selv
	private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {

		for (Message m : activenodes) {
			NodeInterface stub = Util.getProcessStub(m.getNodeName(), m.getPort());
			if (stub != null) {
				stub.onMutexRequestReceived(message);
			}
		}
	}

	// 2. Håndter mottatt forespørsel
	public void onMutexRequestReceived(Message message) throws RemoteException {

		clock.updateClock(message.getClock());

		// Hvis meldingen er fra meg selv, godkjenn umiddelbart
		if (message.getNodeID().equals(node.getNodeID())) {
			onMutexAcknowledgementReceived(message);
			return;
		}

		int caseid = -1;

		if (!CS_BUSY && !WANTS_TO_ENTER_CS) {
			caseid = 0; // Receiver bryr seg ikke
		} else if (CS_BUSY) {
			caseid = 1; // Receiver er i CS
		} else if (WANTS_TO_ENTER_CS) {
			caseid = 2; // Begge vil inn, sjekk tidsstempel
		}

		doDecisionAlgorithm(message, mutexqueue, caseid);
	}

	// 3. Avgjørelsesalgoritme (Hvem vinner?)
	public void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {

		switch (condition) {

			case 0: {
				// Gi tillatelse med en gang
				NodeInterface sender = Util.getProcessStub(message.getNodeName(), message.getPort());
				if (sender != null) sender.onMutexAcknowledgementReceived(message);
				break;
			}

			case 1: {
				// Jeg er opptatt, sett forespørselen i kø
				queue.add(message);
				break;
			}

			case 2: {
				// Sammenlign klokker (Lamport)
				int senderClock = message.getClock();
				int ownClock = clock.getClock();

				boolean senderWins = false;
				if (senderClock < ownClock) {
					senderWins = true;
				} else if (senderClock == ownClock) {
					// Tie-break med NodeID dersom klokken er lik
					if (message.getNodeID().compareTo(node.getNodeID()) < 0) {
						senderWins = true;
					}
				}

				if (senderWins) {
					NodeInterface sender = Util.getProcessStub(message.getNodeName(), message.getPort());
					if (sender != null) sender.onMutexAcknowledgementReceived(message);
				} else {
					queue.add(message);
				}
				break;
			}
			default: break;
		}
	}

	public void onMutexAcknowledgementReceived(Message message) throws RemoteException {
		queueack.add(message);
	}

	// 4. Send "OK" til alle noder som ble satt på vent
	public void multicastReleaseLocks(Set<Message> activenodes) {

		for (Message m : mutexqueue) {
			try {
				NodeInterface stub = Util.getProcessStub(m.getNodeName(), m.getPort());
				if (stub != null) stub.onMutexAcknowledgementReceived(m);
			} catch (RemoteException e) {
				logger.error("Could not release lock for node: " + m.getNodeName());
			}
		}
		mutexqueue.clear();
	}

	private boolean areAllMessagesReturned(int numvoters) throws RemoteException {

		if (queueack.size() == numvoters) {
			queueack.clear();
			return true;
		}
		return false;
	}

	private List<Message> removeDuplicatePeersBeforeVoting() {

		List<Message> uniquepeer = new ArrayList<Message>();
		for (Message p : node.activenodesforfile) {
			boolean found = false;
			for (Message p1 : uniquepeer) {
				if (p.getNodeName().equals(p1.getNodeName())) {
					found = true;
					break;
				}
			}
			if (!found)
				uniquepeer.add(p);
		}
		return uniquepeer;
	}
}