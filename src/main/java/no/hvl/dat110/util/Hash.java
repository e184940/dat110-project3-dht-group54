package no.hvl.dat110.util;

/**
 * exercise/demo purpose in dat110
 * @author tdoy
 *
 */

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {

	/**
	 * Convert any String (node, filename, etc) to a unique numeric
	 * position on the hash ring using MD5 hashing (128-bit digest).
	 * @param entity The String to be hashed
	 * @return BigInteger representing the MD% hash of input.
	 * @throws NoSuchAlgorithmException if MD5 alg not available
	 * @throws UnsupportedEncodingException if UTF-8 encoding not available
	 */
	public static BigInteger hashOf(String entity) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		
		BigInteger hashint = null;
		
		// Task: Hash a given string using MD5 and return the result as a BigInteger.
		// we use MD5 with 128 bits digest
		MessageDigest md = MessageDigest.getInstance("MD5");

		// compute the hash of the input 'entity'
		byte[] digest  = md.digest(entity.getBytes("UTF-8"));

		// convert the hash into hex format
		String hh = toHex(digest);

		// convert the hex into BigInteger
		hashint = new BigInteger(hh, 16);

		// return the BigInteger
		return hashint;
	}

	/**
	 * Compute total size of hash space, how many positions exist on the ring
	 * @return BigInteger value of the address size of the hash space
	 * @throws NoSuchAlgorithmException if MD5 alg not available
	 */
	public static BigInteger addressSize() throws NoSuchAlgorithmException {

		// Task: compute the address size of MD5
		MessageDigest md = MessageDigest.getInstance("MD5");
		
		// compute the number of bits = bitSize()
		int bits = bitSize();
		
		// compute the address size = 2 ^ number of bits
		BigInteger adrsize = BigInteger.TWO.pow(bits);

		// return the address size
		return adrsize;
	}

	/**
	 * Compute the number of bits of the MD5 digest
	 * @return NUmber of bits in the digest
	 * @throws NoSuchAlgorithmException if MD5 alg not available
	 */
	public static int bitSize() throws NoSuchAlgorithmException {
		
		int digestlen = 0;
		
		MessageDigest md = MessageDigest.getInstance("MD5");
		digestlen = md.getDigestLength();
		
		return digestlen*8;
	}

	public static String toHex(byte[] digest) {
		StringBuilder strbuilder = new StringBuilder();
		for(byte b : digest) {
			strbuilder.append(String.format("%02x", b&0xff));
		}
		return strbuilder.toString();
	}

}
