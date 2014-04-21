package enwei;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

/**
 * The manager class that stores one set of encryption/decryption keys (RSA and
 * DES). Also contains bytecode to key methods
 * 
 */
public class Keys {
	private final int RSAKeySize = 1024;
	private KeyPairGenerator RSAKeyGen;
	private KeyGenerator DESkeyGen;

	private KeyPair serverKeyPair;
	private PublicKey serverPubKey;
	private PrivateKey serverPrivKey;
	private Key DESkey;

	private KeyFactory kf;
	private X509EncodedKeySpec ks;

	private final SecureRandom random = new SecureRandom();

	/**
	 * Creates the keys manager class that contains no keys initially
	 */
	public Keys() {
		try {
			RSAKeyGen = KeyPairGenerator.getInstance("RSA");
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("RSAKeyGen setup error");
		}
		try {
			DESkeyGen = KeyGenerator.getInstance("DES");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			System.err.println("DESKeyGen setup error.");
		}
		try {
			kf = KeyFactory.getInstance("RSA");
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Unable to setup Key Factory");
		}
	}

	/**
	 * generates a RSA key pair and overrides any previous RSA key pair
	 */
	public void generateRSAKeyPair() {
		RSAKeyGen.initialize(RSAKeySize, random);
		serverKeyPair = RSAKeyGen.generateKeyPair();
		serverPubKey = serverKeyPair.getPublic();
		serverPrivKey = serverKeyPair.getPrivate();
	}

	/**
	 * generates a DES key and overrides any previous DES key
	 */
	public void generateDESKey() {
		DESkeyGen.init(56, random);
		DESkey = DESkeyGen.generateKey();
	}

	/**
	 * Sets a RSA key pair and overrides any previous RSA key pair
	 */
	public void setRSAKeyPair(KeyPair kp) {
		this.serverKeyPair = kp;
		serverPubKey = serverKeyPair.getPublic();
		serverPrivKey = serverKeyPair.getPrivate();
	}

	/**
	 * Sets a DES key and overrides any previous DES key
	 */
	public void setDESKey(Key k) {
		this.DESkey = k;
	}

	/**
	 * returns the RSA public key currently stored in Keys
	 */
	public PublicKey getRSAPubKey() {
		return serverPubKey;
	}

	/**
	 * returns the RSA private key currently stored in Keys
	 */
	public PrivateKey getRSAPrivKey() {
		return serverPrivKey;
	}

	/**
	 * returns the DES key currently stored in Keys
	 */
	public Key getDESKey() {
		return DESkey;
	}

	/**
	 * converts a byte-encoded key to a RSA public key
	 * 
	 * @param encodedKey
	 *            the byte encoded key
	 * @return a RSA public key
	 * @throws InvalidKeySpecException
	 *             if the byte encoded key is not valid
	 */
	public PublicKey PublicKeyFromByteCode(byte[] encodedKey)
			throws Exception {
		ks = new X509EncodedKeySpec(encodedKey);
		return kf.generatePublic(ks);
	}

	/**
	 * converts a byte-encoded key to a DES key
	 * 
	 * @param encodedKey
	 *            the byte encoded key
	 * @return a DES key
	 */
	public Key DESKeyFromByteCode(byte[] encodedKey) throws Exception {
		Key DESkey = new SecretKeySpec(encodedKey, 0, encodedKey.length, "DES");
		return DESkey;
	}
}
