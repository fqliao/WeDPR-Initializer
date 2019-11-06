package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.common.PublicKeyCrypto;
import java.math.BigInteger;
import java.security.spec.ECFieldFp;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.EllipticCurve;
import javax.crypto.Cipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.jce.spec.IESParameterSpec;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Point;

/**
 * Secp256k1 public key encryption algorithm example. NOTICE:This public key encryption algorithm is
 * not suitable for mass data encryption and decryption.
 *
 * @author caryliao
 * @date 2019/10/21
 */
public class PublicKeyCryptoExample implements PublicKeyCrypto {

    // ECDSA secp256k1 algorithm constants
    private final BigInteger POINTG_PRE =
            new BigInteger("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798", 16);
    private final BigInteger POINTG_POST =
            new BigInteger("483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8", 16);
    private final BigInteger FACTOR_N =
            new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);
    private final BigInteger FIELD_P =
            new BigInteger("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", 16);

    private final IESParameterSpec IES_PARAMS = new IESParameterSpec(null, null, 64);

    @Override
    public byte[] encrypt(byte[] publicKey, byte[] data) throws Exception {
        ECNamedCurveSpec ecNamedCurveSpec = getSecp256k1Curve();
        // Handle public key.
        String publicKeyValue = new BigInteger(publicKey).toString(16);
        String prePublicKeyStr = publicKeyValue.substring(0, 64);
        String postPublicKeyStr = publicKeyValue.substring(64);
        SecP256K1Curve secP256K1Curve = new SecP256K1Curve();
        SecP256K1Point secP256K1Point =
                (SecP256K1Point)
                        secP256K1Curve.createPoint(
                                new BigInteger(prePublicKeyStr, 16),
                                new BigInteger(postPublicKeyStr, 16));
        SecP256K1Point secP256K1PointG =
                (SecP256K1Point) secP256K1Curve.createPoint(POINTG_PRE, POINTG_POST);

        ECDomainParameters domainParameters =
                new ECDomainParameters(secP256K1Curve, secP256K1PointG, FACTOR_N);
        ECPublicKeyParameters publicKeyParameters =
                new ECPublicKeyParameters(secP256K1Point, domainParameters);
        BCECPublicKey bcecPublicKey =
                new BCECPublicKey(
                        "ECDSA",
                        publicKeyParameters,
                        ecNamedCurveSpec,
                        BouncyCastleProvider.CONFIGURATION);

        // Encrypt data.
        Cipher cipher = Cipher.getInstance("ECIES", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, bcecPublicKey, IES_PARAMS);

        return cipher.doFinal(data);
    }

    @Override
    public byte[] decrypt(byte[] secretKey, byte[] data) throws Exception {
        ECNamedCurveSpec ecNamedCurveSpec = getSecp256k1Curve();
        // Handle secret key
        BigInteger secretKeyValue = new BigInteger(secretKey);
        ECPrivateKeySpec secretKeySpec = new ECPrivateKeySpec(secretKeyValue, ecNamedCurveSpec);
        BCECPrivateKey bcecSecretKey =
                new BCECPrivateKey("ECDSA", secretKeySpec, BouncyCastleProvider.CONFIGURATION);

        Cipher cipher = Cipher.getInstance("ECIES", "BC");
        cipher.init(Cipher.DECRYPT_MODE, bcecSecretKey, IES_PARAMS);

        return cipher.doFinal(data);
    }

    /**
     * Get secp256k1 curve.(y^2 = x^3 + 7)
     *
     * @return
     */
    private ECNamedCurveSpec getSecp256k1Curve() {
        EllipticCurve ellipticCurve =
                new EllipticCurve(new ECFieldFp(FIELD_P), new BigInteger("0"), new BigInteger("7"));
        ECPoint pointG = new ECPoint(POINTG_PRE, POINTG_POST);
        ECNamedCurveSpec ecNamedCurveSpec =
                new ECNamedCurveSpec("secp256k1", ellipticCurve, pointG, FACTOR_N);
        return ecNamedCurveSpec;
    }
}
