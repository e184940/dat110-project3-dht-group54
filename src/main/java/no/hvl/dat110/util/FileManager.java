package no.hvl.dat110.util;


/**
 * @author tdoy
 * dat110 - project 3
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.rpc.interfaces.NodeInterface;

public class FileManager {
	
	private static final Logger logger = LogManager.getLogger(FileManager.class);
	
	private BigInteger[] replicafiles;							// array stores replicated files for distribution to matching nodes
	private int numReplicas;									// let's assume each node manages nfiles (5 for now) - can be changed from the constructor
	private NodeInterface chordnode;
	private String filepath; 									// absolute filepath
	private String filename;									// only filename without path and extension
	private BigInteger hash;
	private byte[] bytesOfFile;
	private String sizeOfByte;
	
	private Set<Message> activeNodesforFile = null;
	
	public FileManager(NodeInterface chordnode) throws RemoteException {
		this.chordnode = chordnode;
	}
	
	public FileManager(NodeInterface chordnode, int N) throws RemoteException {
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}
	
	public FileManager(NodeInterface chordnode, String filepath, int N) throws RemoteException {
		this.filepath = filepath;
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}

	/**
	 * Generates numreplicas = N rep. keys by appending an
	 * index to filename and hashing each one with MD5. Hashes stored in replicafiles
	 *
	 * @throws UnsupportedEncodingException if UTF-8 encoding not available
	 * @throws NoSuchAlgorithmException if MD5 alg not available
	 */
	public void createReplicaFiles() throws UnsupportedEncodingException, NoSuchAlgorithmException {

		// set a loop where size = numReplicas
		for (int i = 0; i < numReplicas; i++) {
			// replicate by adding the index to filename
			String replicaFilename = filename + i;

			// hash the replica filename
			// store the hash in the replicafiles array.
			replicafiles[i] = Hash.hashOf(replicaFilename);
		}
	}
	
    /**
     * Take each replica hash, find responsible node on ring, and store file content there.
	 * One replica is randomly chosen as orimary for Remote-Write Protocol¨
	 *
     * @return number of replicas distributed
     * @throws RemoteException if an RMI comm.err occurs
	 * @throws UnsupportedEncodingException if UTF-8 encoding not available
	 * @throws NoSuchAlgorithmException if MD5 alg not available
     */
    public int distributeReplicastoPeers() throws RemoteException, UnsupportedEncodingException, NoSuchAlgorithmException {
    	
    	// randomly appoint the primary server to this file replicas
    	Random rnd = new Random(); 							
    	int index = rnd.nextInt(Util.numReplicas-1);
    	
    	int counter = 0;
	
    	// Task1: Given a filename, make replicas and distribute them to all active peers such that: pred < replica <= peer

    	// Task2: assign a replica as the primary for this file. Hint, see the slide (project 3) on Canvas
    	
    	// create replicas of the filename
		createReplicaFiles();
		// iterate over the replicas
    	for (int i = 0; i < replicafiles.length; i++) {
			BigInteger replica = replicafiles[i];

			// for each replica, find its successor (peer/node) by performing findSuccessor(replica)
			NodeInterface successor = chordnode.findSuccessor(replica);

			if(successor != null){
				// call the addKey on the successor and add the replica
				successor.addKey(replica);

				// implement a logic to decide if this successor should be assigned as the primary for the file
				boolean isPrimary = (i == index);

				// call the saveFileContent() on the successor and set isPrimary=true if logic above is true otherwise set isPrimary=false
				successor.saveFileContent(filename, replica, bytesOfFile, isPrimary);

				// increment counter
				counter++;
			}
		}
		return counter;
    }
	
	/**
	 * Finds all active nodes currently holding a replica of given file.
	 * Locates each of N rep hashes on the ring and retrieves the msg of the replica from the successor of the file.
	 * Metadata for all active nodes having the replica of the file is stored in activeNodesforFile set.
	 *
	 * @param filename the name of file, without patand extensions
	 * @return list of active nodes having the replicas of this file
	 * @throws RemoteException if an RMI comm.err occurs
	 * @throws UnsupportedEncodingException if UTF-8 encoding not available
	 * @throws NoSuchAlgorithmException if MD5 alg not available
	 */
	public Set<Message> requestActiveNodesForFile(String filename) throws RemoteException, UnsupportedEncodingException, NoSuchAlgorithmException {

		this.filename = filename;
		activeNodesforFile = new HashSet<Message>(); 

		// Task: Given a filename, find all the peers that hold a copy of this file
		
		// generate the N replicas from the filename by calling createReplicaFiles()
		createReplicaFiles();

		// iterate over the replicas of the file
		for (int i = 0; i < replicafiles.length; i++) {
			BigInteger replica = replicafiles[i];

			// for each replica, do findSuccessor(replica) that returns successor s.
			NodeInterface successor = chordnode.findSuccessor(replica);

			// get the metadata (Message) of the replica from the successor (i.e., active peer) of the file
			if (successor != null) {
				Message metadata = successor.getFilesMetadata(replica);
				// save the metadata in the set activeNodesforFile.
				if (metadata != null) activeNodesforFile.add(metadata);
			}
		}
		return activeNodesforFile;
	}
	
	/**
	 * Find the primary server - Remote-Write Protocol
	 *
	 * @return the stub of primary server for this file, null if not found
	 */
	public NodeInterface findPrimaryOfItem() {

		// Task: Given all the active peers of a file (activeNodesforFile()), find which is holding the primary copy
		
		// iterate over the activeNodesforFile
		// for each active peer (saved as Message)
		for (Message msg : activeNodesforFile) {

			// use the primaryServer boolean variable contained in the Message class to check if it is the primary or not
			if (msg.isPrimaryServer()){

				// return the primary when found (i.e., use Util.getProcessStub to get the stub and return it)
				return Util.getProcessStub(msg.getNodeName(), msg.getPort());
			}
		}
		return null; 
	}
	
    /**
     * Read the content of a file and return the bytes
     * @throws IOException 
     * @throws NoSuchAlgorithmException 
     */
    public void readFile() throws IOException, NoSuchAlgorithmException {
    	
    	File f = new File(filepath);
    	
    	byte[] bytesOfFile = new byte[(int) f.length()];
    	
		FileInputStream fis = new FileInputStream(f);
        
        fis.read(bytesOfFile);
		fis.close();
		
		//set the values
		filename = f.getName().replace(".txt", "");		
		hash = Hash.hashOf(filename);
		this.bytesOfFile = bytesOfFile;
		double size = (double) bytesOfFile.length/1000;
		NumberFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		sizeOfByte = nf.format(size);
		
		logger.info("filename="+filename+" size="+sizeOfByte);
    	
    }
    
    public void printActivePeers() {
    	
    	activeNodesforFile.forEach(m -> {
    		String peer = m.getNodeName();
    		String id = m.getNodeID().toString();
    		String name = m.getNameOfFile();
    		String hash = m.getHashOfFile().toString();
    		int size = m.getBytesOfFile().length;
    		
    		logger.info(peer+": ID = "+id+" | filename = "+name+" | HashOfFile = "+hash+" | size ="+size);
    		
    	});
    }

	/**
	 * @return the numReplicas
	 */
	public int getNumReplicas() {
		return numReplicas;
	}
	
	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}
	/**
	 * @param filename the filename to set
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}
	/**
	 * @return the hash
	 */
	public BigInteger getHash() {
		return hash;
	}
	/**
	 * @param hash the hash to set
	 */
	public void setHash(BigInteger hash) {
		this.hash = hash;
	}
	/**
	 * @return the bytesOfFile
	 */ 
	public byte[] getBytesOfFile() {
		return bytesOfFile;
	}
	/**
	 * @param bytesOfFile the bytesOfFile to set
	 */
	public void setBytesOfFile(byte[] bytesOfFile) {
		this.bytesOfFile = bytesOfFile;
	}
	/**
	 * @return the size
	 */
	public String getSizeOfByte() {
		return sizeOfByte;
	}
	/**
	 * @param size the size to set
	 */
	public void setSizeOfByte(String sizeOfByte) {
		this.sizeOfByte = sizeOfByte;
	}

	/**
	 * @return the chordnode
	 */
	public NodeInterface getChordnode() {
		return chordnode;
	}

	/**
	 * @return the activeNodesforFile
	 */
	public Set<Message> getActiveNodesforFile() {
		return activeNodesforFile;
	}

	/**
	 * @return the replicafiles
	 */
	public BigInteger[] getReplicafiles() {
		return replicafiles;
	}

	/**
	 * @param filepath the filepath to set
	 */
	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}
}
