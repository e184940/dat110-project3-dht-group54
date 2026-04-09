package no.hvl.dat110.util;

import java.io.Serializable;

/**
 * @author tdoy
 */
public class LamportClock implements Serializable {

	private static final long serialVersionUID = 5030947794470613310L;

	private int clock = 0;

	public synchronized void increment() {
		clock++;
	}

	/**
	 * Brukes når en node mottar en melding med et tidsstempel.
	 * Logikken følger regelen: $L(e) = \max(L(i), L(m)) + 1$
	 * Men i denne implementasjonen gjør vi ofte increment separat,
	 * så vi oppdaterer til max her.
	 */
	public synchronized void updateClock(int remoteClock) {
		// Finn den høyeste verdien av lokal klokke og mottatt klokke
		this.clock = Math.max(this.clock, remoteClock);
	}

	public synchronized void adjustClock(int clock) {
		this.clock = clock;
	}

	public synchronized int getClock() {
		return clock;
	}
}