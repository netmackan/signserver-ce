/*
 * copied from upstream git, this is needed for the bugfixed TimeStampRequest
 * using code from github.com/bcgit/bc-java git tree bea9d91835fabcde3be7ec7f5e9b9d9bddcb34cf
 */
package org.signserver.module.tsa.bc;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.ess.ESSCertID;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificate;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.tsp.Accuracy;
import org.bouncycastle.asn1.tsp.MessageImprint;
import org.bouncycastle.asn1.tsp.TSTInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuerSerial;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSAttributeTableGenerationException;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.CollectionStore;
import org.bouncycastle.util.Store;

/**
 * Currently the class supports ESSCertID by if a digest calculator based on SHA1 is passed in, otherwise it uses
 * ESSCertIDv2. In the event you need to pass both types, you will need to override the SignedAttributeGenerator
 * for the SignerInfoGeneratorBuilder you are using. For the default for ESSCertIDv2 the code will look something
 * like the following:
 * <pre>
 * final ESSCertID essCertid = new ESSCertID(certHashSha1, issuerSerial);
 * final ESSCertIDv2 essCertidV2 = new ESSCertIDv2(certHashSha256, issuerSerial);
 *
 * signerInfoGenBuilder.setSignedAttributeGenerator(new CMSAttributeTableGenerator()
 * {
 *     public AttributeTable getAttributes(Map parameters)
 *         throws CMSAttributeTableGenerationException
 *     {
 *         CMSAttributeTableGenerator attrGen = new DefaultSignedAttributeTableGenerator();
 *
 *         AttributeTable table = attrGen.getAttributes(parameters);
 *
 *         table = table.add(PKCSObjectIdentifiers.id_aa_signingCertificate, new SigningCertificate(essCertid));
 *         table = table.add(PKCSObjectIdentifiers.id_aa_signingCertificateV2, new SigningCertificateV2(essCertidV2));
 *
 *         return table;
 *     }
 * });
 * </pre>
 */
public class TimeStampTokenGenerator
{
    int accuracySeconds = -1;

    int accuracyMillis = -1;

    int accuracyMicros = -1;

    boolean ordering = false;

    GeneralName tsa = null;
    
    private ASN1ObjectIdentifier  tsaPolicyOID;

    private List certs = new ArrayList();
    private List crls = new ArrayList();
    private List attrCerts = new ArrayList();
    private Map otherRevoc = new HashMap();
    private SignerInfoGenerator signerInfoGen;

    /**
     * Basic Constructor - set up a calculator based on signerInfoGen with a ESSCertID calculated from
     * the signer's associated certificate using the sha1DigestCalculator. If alternate values are required
     * for id-aa-signingCertificate they should be added to the signerInfoGen object before it is passed in,
     * otherwise a standard digest based value will be added.
     *
     * @param signerInfoGen the generator for the signer we are using.
     * @param digestCalculator calculator for to use for digest of certificate.
     * @param tsaPolicy tasPolicy to send.
     * @throws IllegalArgumentException if calculator is not SHA-1 or there is no associated certificate for the signer,
     * @throws TSPException if the signer certificate cannot be processed.
     */
    public TimeStampTokenGenerator(
        final SignerInfoGenerator       signerInfoGen,
        DigestCalculator                digestCalculator,
        ASN1ObjectIdentifier            tsaPolicy)
        throws IllegalArgumentException, TSPException
    {
        this(signerInfoGen, digestCalculator, tsaPolicy, false);
    }

    /**
     * Basic Constructor - set up a calculator based on signerInfoGen with a ESSCertID calculated from
     * the signer's associated certificate using the sha1DigestCalculator. If alternate values are required
     * for id-aa-signingCertificate they should be added to the signerInfoGen object before it is passed in,
     * otherwise a standard digest based value will be added.
     *
     * @param signerInfoGen the generator for the signer we are using.
     * @param digestCalculator calculator for to use for digest of certificate.
     * @param tsaPolicy tasPolicy to send.
     * @param isIssuerSerialIncluded should issuerSerial be included in the ESSCertIDs, true if yes, by default false.
     * @throws IllegalArgumentException if calculator is not SHA-1 or there is no associated certificate for the signer,
     * @throws TSPException if the signer certificate cannot be processed.
     */
    public TimeStampTokenGenerator(
        final SignerInfoGenerator       signerInfoGen,
        DigestCalculator                digestCalculator,
        ASN1ObjectIdentifier            tsaPolicy,
        boolean                         isIssuerSerialIncluded)
        throws IllegalArgumentException, TSPException
    {
        this.signerInfoGen = signerInfoGen;
        this.tsaPolicyOID = tsaPolicy;

        if (!signerInfoGen.hasAssociatedCertificate())
        {
            throw new IllegalArgumentException("SignerInfoGenerator must have an associated certificate");
        }

        X509CertificateHolder assocCert = signerInfoGen.getAssociatedCertificate();
        TSPUtil.validateCertificate(assocCert);

        try
        {
            OutputStream dOut = digestCalculator.getOutputStream();

            dOut.write(assocCert.getEncoded());

            dOut.close();

            if (digestCalculator.getAlgorithmIdentifier().getAlgorithm().equals(OIWObjectIdentifiers.idSHA1))
            {
                final ESSCertID essCertid = new ESSCertID(digestCalculator.getDigest(),
                                            isIssuerSerialIncluded ? new IssuerSerial(new GeneralNames(new GeneralName(assocCert.getIssuer())), assocCert.getSerialNumber())
                                                                   : null);

                this.signerInfoGen = new SignerInfoGenerator(signerInfoGen, new CMSAttributeTableGenerator()
                {
                    public AttributeTable getAttributes(Map parameters)
                        throws CMSAttributeTableGenerationException
                    {
                        AttributeTable table = signerInfoGen.getSignedAttributeTableGenerator().getAttributes(parameters);

                        if (table.get(PKCSObjectIdentifiers.id_aa_signingCertificate) == null)
                        {
                            return table.add(PKCSObjectIdentifiers.id_aa_signingCertificate, new SigningCertificate(essCertid));
                        }

                        return table;
                    }
                }, signerInfoGen.getUnsignedAttributeTableGenerator());
            }
            else
            {
                AlgorithmIdentifier digAlgID = new AlgorithmIdentifier(digestCalculator.getAlgorithmIdentifier().getAlgorithm());
                final ESSCertIDv2   essCertid = new ESSCertIDv2(digAlgID, digestCalculator.getDigest(),
                                                    isIssuerSerialIncluded ? new IssuerSerial(new GeneralNames(new GeneralName(assocCert.getIssuer())), new ASN1Integer(assocCert.getSerialNumber()))
                                                                           : null);

                this.signerInfoGen = new SignerInfoGenerator(signerInfoGen, new CMSAttributeTableGenerator()
                {
                    public AttributeTable getAttributes(Map parameters)
                        throws CMSAttributeTableGenerationException
                    {
                        AttributeTable table = signerInfoGen.getSignedAttributeTableGenerator().getAttributes(parameters);

                        if (table.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2) == null)
                        {
                            return table.add(PKCSObjectIdentifiers.id_aa_signingCertificateV2, new SigningCertificateV2(essCertid));
                        }

                        return table;
                    }
                }, signerInfoGen.getUnsignedAttributeTableGenerator());
            }
        }
        catch (IOException e)
        {
            throw new TSPException("Exception processing certificate.", e);
        }
    }

    /**
     * Add the store of X509 Certificates to the generator.
     *
     * @param certStore  a Store containing X509CertificateHolder objects
     */
    public void addCertificates(
        Store certStore)
    {
        certs.addAll(certStore.getMatches(null));
    }

    /**
     *
     * @param crlStore a Store containing X509CRLHolder objects.
     */
    public void addCRLs(
        Store crlStore)
    {
        crls.addAll(crlStore.getMatches(null));
    }

    /**
     *
     * @param attrStore a Store containing X509AttributeCertificate objects.
     */
    public void addAttributeCertificates(
        Store attrStore)
    {
        attrCerts.addAll(attrStore.getMatches(null));
    }

    /**
     * Add a Store of otherRevocationData to the CRL set to be included with the generated TimeStampToken.
     *
     * @param otherRevocationInfoFormat the OID specifying the format of the otherRevocationInfo data.
     * @param otherRevocationInfos a Store of otherRevocationInfo data to add.
     */
    public void addOtherRevocationInfo(
        ASN1ObjectIdentifier   otherRevocationInfoFormat,
        Store                  otherRevocationInfos)
    {
        otherRevoc.put(otherRevocationInfoFormat, otherRevocationInfos.getMatches(null));
    }

    public void setAccuracySeconds(int accuracySeconds)
    {
        this.accuracySeconds = accuracySeconds;
    }

    public void setAccuracyMillis(int accuracyMillis)
    {
        this.accuracyMillis = accuracyMillis;
    }

    public void setAccuracyMicros(int accuracyMicros)
    {
        this.accuracyMicros = accuracyMicros;
    }

    public void setOrdering(boolean ordering)
    {
        this.ordering = ordering;
    }

    public void setTSA(GeneralName tsa)
    {
        this.tsa = tsa;
    }

    public TimeStampToken generate(
        TimeStampRequest    request,
        BigInteger          serialNumber,
        Date                genTime)
    throws TSPException
    {
        ASN1Sequence empty = new DERSequence();
        return generate(request, serialNumber, genTime, Extensions.getInstance(empty));
    }
    
    public TimeStampToken generate(
        TimeStampRequest    request,
        BigInteger          serialNumber,
        Date                genTime,
        Extensions          additionalExtensions)
        throws TSPException
    {
        ASN1ObjectIdentifier digestAlgOID = request.getMessageImprintAlgOID();

        AlgorithmIdentifier algID = new AlgorithmIdentifier(digestAlgOID, DERNull.INSTANCE);
        MessageImprint      messageImprint = new MessageImprint(algID, request.getMessageImprintDigest());

        Accuracy accuracy = null;
        if (accuracySeconds > 0 || accuracyMillis > 0 || accuracyMicros > 0)
        {
            ASN1Integer seconds = null;
            if (accuracySeconds > 0)
            {
                seconds = new ASN1Integer(accuracySeconds);
            }

            ASN1Integer millis = null;
            if (accuracyMillis > 0)
            {
                millis = new ASN1Integer(accuracyMillis);
            }

            ASN1Integer micros = null;
            if (accuracyMicros > 0)
            {
                micros = new ASN1Integer(accuracyMicros);
            }

            accuracy = new Accuracy(seconds, millis, micros);
        }

        ASN1Boolean derOrdering = null;
        if (ordering)
        {
            derOrdering = ASN1Boolean.getInstance(ordering);
        }

        ASN1Integer  nonce = null;
        if (request.getNonce() != null)
        {
            nonce = new ASN1Integer(request.getNonce());
        }

        ASN1ObjectIdentifier tsaPolicy = tsaPolicyOID;
        if (request.getReqPolicy() != null)
        {
            tsaPolicy = request.getReqPolicy();
        }

        Extensions exts = request.getExtensions();

        if (additionalExtensions != null &&
            additionalExtensions.getExtensionOIDs().length > 0) {
            List<ASN1Encodable> allExtensions =
                    new LinkedList<ASN1Encodable>();
            
            if (exts != null) {
                ASN1Sequence seq = (ASN1Sequence) exts.toASN1Primitive();
                ASN1Encodable[] seqArr = seq.toArray();
                List<ASN1Encodable> seqList = Arrays.asList(seqArr);
                
                allExtensions.addAll(seqList);
            }
            
            ASN1Sequence additionalSeq =
                    (ASN1Sequence) additionalExtensions.toASN1Primitive();
                ASN1Encodable[] additionalSeqArr = additionalSeq.toArray();
                List<ASN1Encodable> additionalSeqList =
                        Arrays.asList(additionalSeqArr);
            allExtensions.addAll(additionalSeqList);
            
            ASN1Sequence allSeq = new DERSequence(allExtensions.toArray(new ASN1Encodable[0]));
            
            exts = Extensions.getInstance(allSeq);
        }

        TSTInfo tstInfo = new TSTInfo(tsaPolicy,
                messageImprint, new ASN1Integer(serialNumber),
                new ASN1GeneralizedTime(genTime), accuracy, derOrdering,
                nonce, tsa, exts);

        try
        {
            CMSSignedDataGenerator  signedDataGenerator = new CMSSignedDataGenerator();

            if (request.getCertReq())
            {
                // TODO: do we need to check certs non-empty?
                signedDataGenerator.addCertificates(new CollectionStore(certs));
                signedDataGenerator.addAttributeCertificates(new CollectionStore(attrCerts));
            }

            signedDataGenerator.addCRLs(new CollectionStore(crls));

            if (!otherRevoc.isEmpty())
            {
                for (Iterator it = otherRevoc.keySet().iterator(); it.hasNext();)
                {
                    ASN1ObjectIdentifier format = (ASN1ObjectIdentifier)it.next();

                    signedDataGenerator.addOtherRevocationInfo(format, new CollectionStore((Collection)otherRevoc.get(format)));
                }
            }

            signedDataGenerator.addSignerInfoGenerator(signerInfoGen);

            byte[] derEncodedTSTInfo = tstInfo.getEncoded(ASN1Encoding.DER);

            CMSSignedData signedData = signedDataGenerator.generate(new CMSProcessableByteArray(PKCSObjectIdentifiers.id_ct_TSTInfo, derEncodedTSTInfo), true);

            return new TimeStampToken(signedData);
        }
        catch (CMSException cmsEx)
        {
            throw new TSPException("Error generating time-stamp token", cmsEx);
        }
        catch (IOException e)
        {
            throw new TSPException("Exception encoding info", e);
        }
    }
}

