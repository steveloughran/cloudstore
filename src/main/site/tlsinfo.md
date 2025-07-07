<!---
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License. See accompanying LICENSE file.
-->

# Command `tlsinfo`

Print out TLS information, and X509 certificates.
```
Usage: tlsinfo [-verbose] [-debug] [<match>]
```

* The `-verbose` option prints the full details about each CA, rather than just their principals' names.
* If a string is passed in, only certificates whose name contains that string (case independent) are logged.  

```
> hadoop jar cloudstore-1.0.jar tlsinfo

1. TLS System Properties
========================

[001]  java.version = "1.8.0_362"
[002]  java.library.path = "/Users/stevel/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:."
[003]  com.sun.net.ssl.checkRevocation = (unset)
[004]  https.protocols = (unset)
[005]  javax.net.ssl.keyStore = (unset)
[006]  javax.net.ssl.keyStorePassword = (unset)
[007]  javax.net.ssl.trustStore = (unset)
[008]  javax.net.ssl.trustStorePassword = (unset)
[009]  jdk.certpath.disabledAlgorithms = (unset)
[010]  jdk.tls.client.cipherSuites = (unset)
[011]  jdk.tls.client.protocols = (unset)
[012]  jdk.tls.disabledAlgorithms = (unset)
[013]  jdk.tls.legacyAlgorithms = (unset)
[014]  jsse.enableSNIExtension = (unset)


2. HTTPS supported protocols
============================

    TLSv1.3
    TLSv1.2
    TLSv1.1
    TLSv1
    SSLv3
    SSLv2Hello

See https://www.java.com/en/configure_crypto.html


3. Certificates from the default certificate manager
====================================================

[001] CN=Trustwave Global ECC P256 Certification Authority, O="Trustwave Holdings, Inc.", L=Chicago, ST=Illinois, C=US: 
[002] CN=Certainly Root E1, O=Certainly, C=US: 
[003] CN=Hongkong Post Root CA 1, O=Hongkong Post, C=HK: 
[004] CN=SecureTrust CA, O=SecureTrust Corporation, C=US: 
[005] CN=Entrust Root Certification Authority - EC1, OU="(c) 2012 Entrust, Inc. - for authorized use only", OU=See www.entrust.net/legal-terms, O="Entrust, Inc.", C=US: 
[006] CN=DigiCert Global Root CA, OU=www.digicert.com, O=DigiCert Inc, C=US: 
[007] OU=Security Communication RootCA1, O=SECOM Trust.net, C=JP: 
[008] CN=Hellenic Academic and Research Institutions RootCA 2015, O=Hellenic Academic and Research Institutions Cert. Authority, L=Athens, C=GR: 
[009] CN=QuoVadis Root CA 2 G3, O=QuoVadis Limited, C=BM: 
[010] CN=Autoridad de Certificacion Firmaprofesional CIF A62634068, C=ES: 
[011] CN=DigiCert Trusted Root G4, OU=www.digicert.com, O=DigiCert Inc, C=US: 
[012] CN=GeoTrust Primary Certification Authority, O=GeoTrust Inc., C=US: 
[013] CN=Hellenic Academic and Research Institutions ECC RootCA 2015, O=Hellenic Academic and Research Institutions Cert. Authority, L=Athens, C=GR: 
[014] CN=emSign ECC Root CA - G3, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN: 
[015] OU=Security Communication RootCA2, O="SECOM Trust Systems CO.,LTD.", C=JP: 
[016] OU=ePKI Root Certification Authority, O="Chunghwa Telecom Co., Ltd.", C=TW: 
[017] CN=AffirmTrust Commercial, O=AffirmTrust, C=US: 
[018] CN=Certum Trusted Network CA, OU=Certum Certification Authority, O=Unizeto Technologies S.A., C=PL: 
[019] CN=AC RAIZ FNMT-RCM SERVIDORES SEGUROS, OID.2.5.4.97=VATES-Q2826004J, OU=Ceres, O=FNMT-RCM, C=ES: 
[020] CN=XRamp Global Certification Authority, O=XRamp Security Services Inc, OU=www.xrampsecurity.com, C=US: 
[021] CN=D-TRUST EV Root CA 1 2020, O=D-Trust GmbH, C=DE: 
[022] CN=emSign Root CA - G1, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN: 
[023] CN=Trustwave Global Certification Authority, O="Trustwave Holdings, Inc.", L=Chicago, ST=Illinois, C=US: 
[024] CN=Entrust Root Certification Authority - G4, OU="(c) 2015 Entrust, Inc. - for authorized use only", OU=See www.entrust.net/legal-terms, O="Entrust, Inc.", C=US: 
[025] CN=GeoTrust Primary Certification Authority - G2, OU=(c) 2007 GeoTrust Inc. - For authorized use only, O=GeoTrust Inc., C=US: 
[026] CN=Telia Root CA v2, O=Telia Finland Oyj, C=FI: 
[027] CN=Certum Trusted Root CA, OU=Certum Certification Authority, O=Asseco Data Systems S.A., C=PL: 
[028] CN=vTrus ECC Root CA, O="iTrusChina Co.,Ltd.", C=CN: 
[029] CN=COMODO ECC Certification Authority, O=COMODO CA Limited, L=Salford, ST=Greater Manchester, C=GB: 
[030] CN=ISRG Root X1, O=Internet Security Research Group, C=US: 
[031] CN=DigiCert High Assurance EV Root CA, OU=www.digicert.com, O=DigiCert Inc, C=US: 
[032] CN=ANF Secure Server Root CA, OU=ANF CA Raiz, O=ANF Autoridad de Certificacion, C=ES, SERIALNUMBER=G63287510: 
[033] CN=TrustCor RootCert CA-1, OU=TrustCor Certificate Authority, O=TrustCor Systems S. de R.L., L=Panama City, ST=Panama, C=PA: 
[034] CN=GeoTrust Universal CA, O=GeoTrust Inc., C=US: 
[035] CN=GlobalSign, O=GlobalSign, OU=GlobalSign Root CA - R3: 
[036] CN=Security Communication RootCA3, O="SECOM Trust Systems CO.,LTD.", C=JP: 
[037] CN=Baltimore CyberTrust Root, OU=CyberTrust, O=Baltimore, C=IE: 
[038] OU=Starfield Class 2 Certification Authority, O="Starfield Technologies, Inc.", C=US: 
[039] CN=AAA Certificate Services, O=Comodo CA Limited, L=Salford, ST=Greater Manchester, C=GB: 
[040] CN=Chambers of Commerce Root, OU=http://www.chambersign.org, O=AC Camerfirma SA CIF A82743287, C=EU: 
[041] CN=VeriSign Class 3 Public Primary Certification Authority - G3, OU="(c) 1999 VeriSign, Inc. - For authorized use only", OU=VeriSign Trust Network, O="VeriSign, Inc.", C=US: 
[042] OU=AC RAIZ FNMT-RCM, O=FNMT-RCM, C=ES: 
[043] CN=GlobalSign Root CA, OU=Root CA, O=GlobalSign nv-sa, C=BE: 
[044] CN=e-Szigno Root CA 2017, OID.2.5.4.97=VATHU-23584497, O=Microsec Ltd., L=Budapest, C=HU: 
[045] CN=AffirmTrust Networking, O=AffirmTrust, C=US: 
[046] CN=TWCA Global Root CA, OU=Root CA, O=TAIWAN-CA, C=TW: 
[047] CN=AffirmTrust Premium, O=AffirmTrust, C=US: 
[048] CN=DigiCert TLS ECC P384 Root G5, O="DigiCert, Inc.", C=US: 
[049] CN=GeoTrust Primary Certification Authority - G3, OU=(c) 2008 GeoTrust Inc. - For authorized use only, O=GeoTrust Inc., C=US: 
[050] CN=TWCA Root Certification Authority, OU=Root CA, O=TAIWAN-CA, C=TW: 
[051] CN=GTS Root R4, O=Google Trust Services LLC, C=US: 
[052] CN=LuxTrust Global Root 2, O=LuxTrust S.A., C=LU: 
[053] CN=Chambers of Commerce Root - 2008, O=AC Camerfirma S.A., SERIALNUMBER=A82743287, L=Madrid (see current address at www.camerfirma.com/address), C=EU: 
[054] C=DE, O=Atos, CN=Atos TrustedRoot 2011: 
[055] CN=SSL.com EV Root Certification Authority RSA R2, O=SSL Corporation, L=Houston, ST=Texas, C=US: 
[056] CN=SecureSign RootCA11, O="Japan Certification Services, Inc.", C=JP: 
[057] CN=SwissSign Silver CA - G2, O=SwissSign AG, C=CH: 
[058] CN=SSL.com Root Certification Authority ECC, O=SSL Corporation, L=Houston, ST=Texas, C=US: 
[059] CN=Entrust Root Certification Authority - G2, OU="(c) 2009 Entrust, Inc. - for authorized use only", OU=See www.entrust.net/legal-terms, O="Entrust, Inc.", C=US: 
[060] OU=Go Daddy Class 2 Certification Authority, O="The Go Daddy Group, Inc.", C=US: 
[061] CN=DigiCert Assured ID Root CA, OU=www.digicert.com, O=DigiCert Inc, C=US: 
[062] CN=TUBITAK Kamu SM SSL Kok Sertifikasi - Surum 1, OU=Kamu Sertifikasyon Merkezi - Kamu SM, O=Turkiye Bilimsel ve Teknolojik Arastirma Kurumu - TUBITAK, L=Gebze - Kocaeli, C=TR: 
[063] CN=TrustCor RootCert CA-2, OU=TrustCor Certificate Authority, O=TrustCor Systems S. de R.L., L=Panama City, ST=Panama, C=PA: 
[064] CN=HARICA TLS ECC Root CA 2021, O=Hellenic Academic and Research Institutions CA, C=GR: 
[065] CN=Secure Global CA, O=SecureTrust Corporation, C=US: 
[066] CN=GTS Root R1, O=Google Trust Services LLC, C=US: 
[067] CN=T-TeleSec GlobalRoot Class 3, OU=T-Systems Trust Center, O=T-Systems Enterprise Services GmbH, C=DE: 
[068] CN=Certigna Root CA, OU=0002 48146308100036, O=Dhimyotis, C=FR: 
[069] CN=DigiCert Global Root G3, OU=www.digicert.com, O=DigiCert Inc, C=US: 
[070] CN=TunTrust Root CA, O=Agence Nationale de Certification Electronique, C=TN: 
[071] CN=Certainly Root R1, O=Certainly, C=US: 
[072] CN=DigiCert TLS RSA4096 Root G5, O="DigiCert, Inc.", C=US: 
[073] CN=TrustCor ECA-1, OU=TrustCor Certificate Authority, O=TrustCor Systems S. de R.L., L=Panama City, ST=Panama, C=PA: 
[074] CN=E-Tugra Global Root CA ECC v3, OU=E-Tugra Trust Center, O=E-Tugra EBG A.S., L=Ankara, C=TR: 
[075] CN=GlobalSign Root R46, O=GlobalSign nv-sa, C=BE: 
[076] CN=GeoTrust Global CA, O=GeoTrust Inc., C=US: 
[077] CN=Network Solutions Certificate Authority, O=Network Solutions L.L.C., C=US: 
[078] CN=SwissSign Platinum CA - G2, O=SwissSign AG, C=CH: 
[079] CN=CFCA EV ROOT, O=China Financial Certification Authority, C=CN: 
[080] CN=GlobalSign, O=GlobalSign, OU=GlobalSign ECC Root CA - R5: 
[081] CN=NAVER Global Root Certification Authority, O=NAVER BUSINESS PLATFORM Corp., C=KR: 
[082] CN=Certum Trusted Network CA 2, OU=Certum Certification Authority, O=Unizeto Technologies S.A., C=PL: 
[083] CN=Starfield Root Certificate Authority - G2, O="Starfield Technologies, Inc.", L=Scottsdale, ST=Arizona, C=US: 
[084] CN=IdenTrust Public Sector Root CA 1, O=IdenTrust, C=US: 
[085] CN=Entrust.net Certification Authority (2048), OU=(c) 1999 Entrust.net Limited, OU=www.entrust.net/CPS_2048 incorp. by ref. (limits liab.), O=Entrust.net: 
[086] CN=TeliaSonera Root CA v1, O=TeliaSonera: 
[087] CN=thawte Primary Root CA, OU="(c) 2006 thawte, Inc. - For authorized use only", OU=Certification Services Division, O="thawte, Inc.", C=US: 
[088] CN=Go Daddy Root Certificate Authority - G2, O="GoDaddy.com, Inc.", L=Scottsdale, ST=Arizona, C=US: 
[089] CN=VeriSign Class 3 Public Primary Certification Authority - G4, OU="(c) 2007 VeriSign, Inc. - For authorized use only", OU=VeriSign Trust Network, O="VeriSign, Inc.", C=US: 
[090] CN=GTS Root R3, O=Google Trust Services LLC, C=US: 
[091] CN=Entrust Root Certification Authority, OU="(c) 2006 Entrust, Inc.", OU=www.entrust.net/CPS is incorporated by reference, O="Entrust, Inc.", C=US: 
[092] OU=certSIGN ROOT CA G2, O=CERTSIGN SA, C=RO: 
[093] CN=DigiCert Assured ID Root G2, OU=www.digicert.com, O=DigiCert Inc, C=US: 
[094] CN=SSL.com Root Certification Authority RSA, O=SSL Corporation, L=Houston, ST=Texas, C=US: 
[095] CN=Amazon Root CA 4, O=Amazon, C=US: 
[096] CN=GlobalSign, O=GlobalSign, OU=GlobalSign Root CA - R6: 
[097] CN=Certum CA, O=Unizeto Sp. z o.o., C=PL: 
[098] CN=OISTE WISeKey Global Root GC CA, OU=OISTE Foundation Endorsed, O=WISeKey, C=CH: 
[099] CN=CA Disig Root R2, O=Disig a.s., L=Bratislava, C=SK: 
[100] CN=Buypass Class 2 Root CA, O=Buypass AS-983163327, C=NO: 
[101] CN=Hongkong Post Root CA 3, O=Hongkong Post, L=Hong Kong, ST=Hong Kong, C=HK: 
[102] CN=D-TRUST Root Class 3 CA 2 EV 2009, O=D-Trust GmbH, C=DE: 
[103] CN=DigiCert Assured ID Root G3, OU=www.digicert.com, O=DigiCert Inc, C=US: 
[104] CN=SwissSign Gold CA - G2, O=SwissSign AG, C=CH: 
[105] CN=USERTrust ECC Certification Authority, O=The USERTRUST Network, L=Jersey City, ST=New Jersey, C=US: 
[106] OU=certSIGN ROOT CA, O=certSIGN, C=RO: 
[107] CN=IdenTrust Commercial Root CA 1, O=IdenTrust, C=US: 
[108] CN=QuoVadis Root CA 2, O=QuoVadis Limited, C=BM: 
[109] CN=QuoVadis Root CA 1 G3, O=QuoVadis Limited, C=BM: 
[110] CN=D-TRUST Root Class 3 CA 2 2009, O=D-Trust GmbH, C=DE: 
[111] CN=vTrus Root CA, O="iTrusChina Co.,Ltd.", C=CN: 
[112] CN=USERTrust RSA Certification Authority, O=The USERTRUST Network, L=Jersey City, ST=New Jersey, C=US: 
[113] CN=Izenpe.com, O=IZENPE S.A., C=ES: 
[114] CN=VeriSign Class 3 Public Primary Certification Authority - G5, OU="(c) 2006 VeriSign, Inc. - For authorized use only", OU=VeriSign Trust Network, O="VeriSign, Inc.", C=US: 
[115] CN=QuoVadis Root CA 3, O=QuoVadis Limited, C=BM: 
[116] CN=Starfield Services Root Certificate Authority - G2, O="Starfield Technologies, Inc.", L=Scottsdale, ST=Arizona, C=US: 
[117] CN=emSign ECC Root CA - C3, O=eMudhra Inc, OU=emSign PKI, C=US: 
[118] CN=OISTE WISeKey Global Root GB CA, OU=OISTE Foundation Endorsed, O=WISeKey, C=CH: 
[119] CN=GlobalSign Root E46, O=GlobalSign nv-sa, C=BE: 
[120] CN=Amazon Root CA 3, O=Amazon, C=US: 
[121] CN=Microsoft RSA Root Certificate Authority 2017, O=Microsoft Corporation, C=US: 
[122] EMAILADDRESS=info@e-szigno.hu, CN=Microsec e-Szigno Root CA 2009, O=Microsec Ltd., L=Budapest, C=HU: 
[123] CN=QuoVadis Root CA 3 G3, O=QuoVadis Limited, C=BM: 
[124] CN=HARICA TLS RSA Root CA 2021, O=Hellenic Academic and Research Institutions CA, C=GR: 
[125] CN=GLOBALTRUST 2020, O=e-commerce monitoring GmbH, C=AT: 
[126] CN=NetLock Arany (Class Gold) Főtanúsítvány, OU=Tanúsítványkiadók (Certification Services), O=NetLock Kft., L=Budapest, C=HU: 
[127] CN=Actalis Authentication Root CA, O=Actalis S.p.A./03358520967, L=Milan, C=IT: 
[128] CN=GlobalSign, O=GlobalSign, OU=GlobalSign ECC Root CA - R4: 
[129] CN=Autoridad de Certificacion Firmaprofesional CIF A62634068, C=ES: 
[130] CN=Certigna, O=Dhimyotis, C=FR: 
[131] CN=E-Tugra Certification Authority, OU=E-Tugra Sertifikasyon Merkezi, O=E-Tuğra EBG Bilişim Teknolojileri ve Hizmetleri A.Ş., L=Ankara, C=TR: 
[132] CN=Certum EC-384 CA, OU=Certum Certification Authority, O=Asseco Data Systems S.A., C=PL: 
[133] C=ES, O=ACCV, OU=PKIACCV, CN=ACCVRAIZ1: 
[134] CN=E-Tugra Global Root CA RSA v3, OU=E-Tugra Trust Center, O=E-Tugra EBG A.S., L=Ankara, C=TR: 
[135] CN=HiPKI Root CA - G1, O="Chunghwa Telecom Co., Ltd.", C=TW: 
[136] CN=GTS Root R2, O=Google Trust Services LLC, C=US: 
[137] CN=SSL.com EV Root Certification Authority ECC, O=SSL Corporation, L=Houston, ST=Texas, C=US: 
[138] CN=Buypass Class 3 Root CA, O=Buypass AS-983163327, C=NO: 
[139] CN=thawte Primary Root CA - G2, OU="(c) 2007 thawte, Inc. - For authorized use only", O="thawte, Inc.", C=US: 
[140] CN=ISRG Root X2, O=Internet Security Research Group, C=US: 
[141] CN=emSign Root CA - C1, O=eMudhra Inc, OU=emSign PKI, C=US: 
[142] CN=VeriSign Universal Root Certification Authority, OU="(c) 2008 VeriSign, Inc. - For authorized use only", OU=VeriSign Trust Network, O="VeriSign, Inc.", C=US: 
[143] CN=Amazon Root CA 1, O=Amazon, C=US: 
[144] CN=Trustwave Global ECC P384 Certification Authority, O="Trustwave Holdings, Inc.", L=Chicago, ST=Illinois, C=US: 
[145] CN=Amazon Root CA 2, O=Amazon, C=US: 
[146] CN=GDCA TrustAUTH R5 ROOT, O="GUANG DONG CERTIFICATE AUTHORITY CO.,LTD.", C=CN: 
[147] CN=UCA Extended Validation Root, O=UniTrust, C=CN: 
[148] CN=COMODO Certification Authority, O=COMODO CA Limited, L=Salford, ST=Greater Manchester, C=GB: 
[149] CN=GlobalSign, O=GlobalSign, OU=GlobalSign ECC Root CA - R4: 
[150] CN=Global Chambersign Root - 2008, O=AC Camerfirma S.A., SERIALNUMBER=A82743287, L=Madrid (see current address at www.camerfirma.com/address), C=EU: 
[151] CN=COMODO RSA Certification Authority, O=COMODO CA Limited, L=Salford, ST=Greater Manchester, C=GB: 
[152] CN=thawte Primary Root CA - G3, OU="(c) 2008 thawte, Inc. - For authorized use only", OU=Certification Services Division, O="thawte, Inc.", C=US: 
[153] CN=UCA Global G2 Root, O=UniTrust, C=CN: 
[154] CN=Microsoft ECC Root Certificate Authority 2017, O=Microsoft Corporation, C=US: 
[155] CN=AffirmTrust Premium ECC, O=AffirmTrust, C=US: 
[156] CN=T-TeleSec GlobalRoot Class 2, OU=T-Systems Trust Center, O=T-Systems Enterprise Services GmbH, C=DE: 
[157] CN=D-TRUST BR Root CA 1 2020, O=D-Trust GmbH, C=DE: 
[158] CN=DigiCert Global Root G2, OU=www.digicert.com, O=DigiCert Inc, C=US: 
[159] CN=SZAFIR ROOT CA2, O=Krajowa Izba Rozliczeniowa S.A., C=PL: 
```

Print certificates containing a specific string, here everything with the string "amazon" in it.
```
> hadoop jar cloudstore-1.0.jar tlsinfo amazon

1. TLS System Properties
========================

[001]  java.version = "11.0.18"
[002]  java.library.path = "/Users/stevel/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:."
[003]  com.sun.net.ssl.checkRevocation = (unset)
[004]  https.protocols = (unset)
[005]  javax.net.ssl.keyStore = (unset)
[006]  javax.net.ssl.keyStorePassword = (unset)
[007]  javax.net.ssl.trustStore = (unset)
[008]  javax.net.ssl.trustStorePassword = (unset)
[009]  jdk.certpath.disabledAlgorithms = (unset)
[010]  jdk.tls.client.cipherSuites = (unset)
[011]  jdk.tls.client.protocols = (unset)
[012]  jdk.tls.disabledAlgorithms = (unset)
[013]  jdk.tls.legacyAlgorithms = (unset)
[014]  jsse.enableSNIExtension = (unset)


2. HTTPS supported protocols
============================

    TLSv1.3
    TLSv1.2
    TLSv1.1
    TLSv1
    SSLv3
    SSLv2Hello

See https://www.java.com/en/configure_crypto.html


3. Certificates from the default certificate manager
====================================================

[001] CN=Amazon Root CA 4, O=Amazon, C=US: 
[002] CN=Amazon Root CA 3, O=Amazon, C=US: 
[003] CN=Amazon Root CA 1, O=Amazon, C=US: 
[004] CN=Amazon Root CA 2, O=Amazon, C=US: 
Number of certificates matching the string "amazon" :4
```
A full dump of the details of matching certificates

```
> hadoop jar cloudstore-1.0.jar tlsinfo -verbose amazon


1. TLS System Properties
========================

[001]  java.version = "11.0.18"
[002]  java.library.path = "/Users/stevel/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:."
[003]  com.sun.net.ssl.checkRevocation = (unset)
[004]  https.protocols = (unset)
[005]  javax.net.ssl.keyStore = (unset)
[006]  javax.net.ssl.keyStorePassword = (unset)
[007]  javax.net.ssl.trustStore = (unset)
[008]  javax.net.ssl.trustStorePassword = (unset)
[009]  jdk.certpath.disabledAlgorithms = (unset)
[010]  jdk.tls.client.cipherSuites = (unset)
[011]  jdk.tls.client.protocols = (unset)
[012]  jdk.tls.disabledAlgorithms = (unset)
[013]  jdk.tls.legacyAlgorithms = (unset)
[014]  jsse.enableSNIExtension = (unset)


2. HTTPS supported protocols
============================

    TLSv1.3
    TLSv1.2
    TLSv1.1
    TLSv1
    SSLv3
    SSLv2Hello

See https://www.java.com/en/configure_crypto.html


3. Certificates from the default certificate manager
====================================================

[001] CN=Amazon Root CA 4, O=Amazon, C=US: [
[
  Version: V3
  Subject: CN=Amazon Root CA 4, O=Amazon, C=US
  Signature Algorithm: SHA384withECDSA, OID = 1.2.840.10045.4.3.3

  Key:  Sun EC public key, 384 bits
  public x coord: 32425092614383372028027580539516716424922338958434127682225080194340688800012356190243950665896377101093574671555360
  public y coord: 23436644459463023632006030778216433944030576499306896693350641748163927445956980141969043148117079480765609995284060
  parameters: secp384r1 [NIST P-384] (1.3.132.0.34)
  Validity: [From: Tue May 26 01:00:00 BST 2015,
               To: Sat May 26 01:00:00 BST 2040]
  Issuer: CN=Amazon Root CA 4, O=Amazon, C=US
  SerialNumber: [    066c9fd7 c1bb104c 2943e571 7b7b2cc8 1ac10e]

Certificate Extensions: 3
[1]: ObjectId: 2.5.29.19 Criticality=true
BasicConstraints:[
  CA:true
  PathLen:2147483647
]

[2]: ObjectId: 2.5.29.15 Criticality=true
KeyUsage [
  DigitalSignature
  Key_CertSign
  Crl_Sign
]

[3]: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: D3 EC C7 3A 65 6E CC E1   DA 76 9A 56 FB 9C F3 86  ...:en...v.V....
0010: 6D 57 E5 81                                        mW..
]
]

]
  Algorithm: [SHA384withECDSA]
  Signature:
0000: 30 65 02 30 3A 8B 21 F1   BD 7E 11 AD D0 EF 58 96  0e.0:.!.......X.
0010: 2F D6 EB 9D 7E 90 8D 2B   CF 66 55 C3 2C E3 28 A9  /......+.fU.,.(.
0020: 70 0A 47 0E F0 37 59 12   FF 2D 99 94 28 4E 2A 4F  p.G..7Y..-..(N*O
0030: 35 4D 33 5A 02 31 00 EA   75 00 4E 3B C4 3A 94 12  5M3Z.1..u.N;.:..
0040: 91 C9 58 46 9D 21 13 72   A7 88 9C 8A E4 4C 4A DB  ..XF.!.r.....LJ.
0050: 96 D4 AC 8B 6B 6B 49 12   53 33 AD D7 E4 BE 24 FC  ....kkI.S3....$.
0060: B5 0A 76 D4 A5 BC 10                               ..v....

]
[002] CN=Amazon Root CA 3, O=Amazon, C=US: [
[
  Version: V3
  Subject: CN=Amazon Root CA 3, O=Amazon, C=US
  Signature Algorithm: SHA256withECDSA, OID = 1.2.840.10045.4.3.2

  Key:  Sun EC public key, 256 bits
  public x coord: 18812778635302664422733345692861019973681640409737460634078102934466316627231
  public y coord: 52244309668154938202224296968350057312816302077526561842726239180030042924766
  parameters: secp256r1 [NIST P-256, X9.62 prime256v1] (1.2.840.10045.3.1.7)
  Validity: [From: Tue May 26 01:00:00 BST 2015,
               To: Sat May 26 01:00:00 BST 2040]
  Issuer: CN=Amazon Root CA 3, O=Amazon, C=US
  SerialNumber: [    066c9fd5 74973666 3f3b0b9a d9e89e76 03f24a]

Certificate Extensions: 3
[1]: ObjectId: 2.5.29.19 Criticality=true
BasicConstraints:[
  CA:true
  PathLen:2147483647
]

[2]: ObjectId: 2.5.29.15 Criticality=true
KeyUsage [
  DigitalSignature
  Key_CertSign
  Crl_Sign
]

[3]: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: AB B6 DB D7 06 9E 37 AC   30 86 07 91 70 C7 9C C4  ......7.0...p...
0010: 19 B1 78 C0                                        ..x.
]
]

]
  Algorithm: [SHA256withECDSA]
  Signature:
0000: 30 46 02 21 00 E0 85 92   A3 17 B7 8D F9 2B 06 A5  0F.!.........+..
0010: 93 AC 1A 98 68 61 72 FA   E1 A1 D0 FB 1C 78 60 A6  ....har......x`.
0020: 43 99 C5 B8 C4 02 21 00   9C 02 EF F1 94 9C B3 96  C.....!.........
0030: F9 EB C6 2A F8 B6 2C FE   3A 90 14 16 D7 8C 63 24  ...*..,.:.....c$
0040: 48 1C DF 30 7D D5 68 3B                            H..0..h;

]
[003] CN=Amazon Root CA 1, O=Amazon, C=US: [
[
  Version: V3
  Subject: CN=Amazon Root CA 1, O=Amazon, C=US
  Signature Algorithm: SHA256withRSA, OID = 1.2.840.113549.1.1.11

  Key:  Sun RSA public key, 2048 bits
  params: null
  modulus: 22529839904807742196558773392430766620630713202204326167346456925862066285712069978308045976033918808540171076811098215136401323342247576789054764683787147408289170989302937775178809187827657352584557953877946352196797789035355954596527030584944622221752357105572088106020206921431118198373122638305846252087992561841631797199384157902018140720267433956687491591657652730221337591680012205319549572614035105482287002884850178224609018864719685310905426619874727796905080238179726224664042154200651710137931048812546957419686875805576245376866031854569863410951649630469236463991472642618512857920826701027482532358669
  public exponent: 65537
  Validity: [From: Tue May 26 01:00:00 BST 2015,
               To: Sun Jan 17 00:00:00 GMT 2038]
  Issuer: CN=Amazon Root CA 1, O=Amazon, C=US
  SerialNumber: [    066c9fcf 99bf8c0a 39e2f078 8a43e696 365bca]

Certificate Extensions: 3
[1]: ObjectId: 2.5.29.19 Criticality=true
BasicConstraints:[
  CA:true
  PathLen:2147483647
]

[2]: ObjectId: 2.5.29.15 Criticality=true
KeyUsage [
  DigitalSignature
  Key_CertSign
  Crl_Sign
]

[3]: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: 84 18 CC 85 34 EC BC 0C   94 94 2E 08 59 9C C7 B2  ....4.......Y...
0010: 10 4E 0A 08                                        .N..
]
]

]
  Algorithm: [SHA256withRSA]
  Signature:
0000: 98 F2 37 5A 41 90 A1 1A   C5 76 51 28 20 36 23 0E  ..7ZA....vQ( 6#.
0010: AE E6 28 BB AA F8 94 AE   48 A4 30 7F 1B FC 24 8D  ..(.....H.0...$.
0020: 4B B4 C8 A1 97 F6 B6 F1   7A 70 C8 53 93 CC 08 28  K.......zp.S...(
0030: E3 98 25 CF 23 A4 F9 DE   21 D3 7C 85 09 AD 4E 9A  ..%.#...!.....N.
0040: 75 3A C2 0B 6A 89 78 76   44 47 18 65 6C 8D 41 8E  u:..j.xvDG.el.A.
0050: 3B 7F 9A CB F4 B5 A7 50   D7 05 2C 37 E8 03 4B AD  ;......P..,7..K.
0060: E9 61 A0 02 6E F5 F2 F0   C5 B2 ED 5B B7 DC FA 94  .a..n......[....
0070: 5C 77 9E 13 A5 7F 52 AD   95 F2 F8 93 3B DE 8B 5C  \w....R.....;..\
0080: 5B CA 5A 52 5B 60 AF 14   F7 4B EF A3 FB 9F 40 95  [.ZR[`...K....@.
0090: 6D 31 54 FC 42 D3 C7 46   1F 23 AD D9 0F 48 70 9A  m1T.B..F.#...Hp.
00A0: D9 75 78 71 D1 72 43 34   75 6E 57 59 C2 02 5C 26  .uxq.rC4unWY..\&
00B0: 60 29 CF 23 19 16 8E 88   43 A5 D4 E4 CB 08 FB 23  `).#....C......#
00C0: 11 43 E8 43 29 72 62 A1   A9 5D 5E 08 D4 90 AE B8  .C.C)rb..]^.....
00D0: D8 CE 14 C2 D0 55 F2 86   F6 C4 93 43 77 66 61 C0  .....U.....Cwfa.
00E0: B9 E8 41 D7 97 78 60 03   6E 4A 72 AE A5 D1 7D BA  ..A..x`.nJr.....
00F0: 10 9E 86 6C 1B 8A B9 59   33 F8 EB C4 90 BE F1 B9  ...l...Y3.......

]
[004] CN=Amazon Root CA 2, O=Amazon, C=US: [
[
  Version: V3
  Subject: CN=Amazon Root CA 2, O=Amazon, C=US
  Signature Algorithm: SHA384withRSA, OID = 1.2.840.113549.1.1.12

  Key:  Sun RSA public key, 4096 bits
  params: null
  modulus: 708178749122597559619527357425477674602083011336670379170847091246858816376845345304142379194036832377511925041943867501585433284248583942166760906465083860074733960252636158658394496617817777606275448962993134677571150362467074551614405446632983486529060071089382723776594821998335099394748961634055831013454299606744598489076288234094913297605490773964066889512355618592938258417986721317412685065532176782392073874109425052398615470576936506648347749651547580628294628315883358417174297986861294533161469987034455474779756076894883323044421570905184561986901744207035808458461553627409324358589631965160184474284108080344681163488126519525176756441192720200884868096002886471281086918627557911249511671438642390318291317643219524762706562191481905864333874069740445361179182400839255529828212707059931182582177496621567658489380388490513664305221272079833753155989218945371015618081949415624892373616341417821600897825144201630247817718903681002554099025941172190253869893987073694758167582645736012675745116136853920066693038669335422069858853077276445207650702127217866583632971009151078158588663748230191719451857150240000082621911071322221673244497652325337531186548728751397679111014332051256988410868977801232903372659354543
  public exponent: 65537
  Validity: [From: Tue May 26 01:00:00 BST 2015,
               To: Sat May 26 01:00:00 BST 2040]
  Issuer: CN=Amazon Root CA 2, O=Amazon, C=US
  SerialNumber: [    066c9fd2 9635869f 0a0fe586 78f85b26 bb8a37]

Certificate Extensions: 3
[1]: ObjectId: 2.5.29.19 Criticality=true
BasicConstraints:[
  CA:true
  PathLen:2147483647
]

[2]: ObjectId: 2.5.29.15 Criticality=true
KeyUsage [
  DigitalSignature
  Key_CertSign
  Crl_Sign
]

[3]: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: B0 0C F0 4C 30 F4 05 58   02 48 FD 33 E5 52 AF 4B  ...L0..X.H.3.R.K
0010: 84 E3 66 52                                        ..fR
]
]

]
  Algorithm: [SHA384withRSA]
  Signature:
0000: AA A8 80 8F 0E 78 A3 E0   A2 D4 CD E6 F5 98 7A 3B  .....x........z;
0010: EA 00 03 B0 97 0E 93 BC   5A A8 F6 2C 8C 72 87 A9  ........Z..,.r..
0020: B1 FC 7F 73 FD 63 71 78   A5 87 59 CF 30 E1 0D 10  ...s.cqx..Y.0...
0030: B2 13 5A 6D 82 F5 6A E6   80 9F A0 05 0B 68 E4 47  ..Zm..j......h.G
0040: 6B C7 6A DF B6 FD 77 32   72 E5 18 FA 09 F4 A0 93  k.j...w2r.......
0050: 2C 5D D2 8C 75 85 76 65   90 0C 03 79 B7 31 23 63  ,]..u.ve...y.1#c
0060: AD 78 83 09 86 68 84 CA   FF F9 CF 26 9A 92 79 E7  .x...h.....&..y.
0070: CD 4B C5 E7 61 A7 17 CB   F3 A9 12 93 93 6B A7 E8  .K..a........k..
0080: 2F 53 92 C4 60 58 B0 CC   02 51 18 5B 85 8D 62 59  /S..`X...Q.[..bY
0090: 63 B6 AD B4 DE 9A FB 26   F7 00 27 C0 5D 55 37 74  c......&..'.]U7t
00A0: 99 C9 50 7F E3 59 2E 44   E3 2C 25 EE EC 4C 32 77  ..P..Y.D.,%..L2w
00B0: B4 9F 1A E9 4B 5D 20 C5   DA FD 1C 87 16 C6 43 E8  ....K] .......C.
00C0: D4 BB 26 9A 45 70 5E A9   0B 37 53 E2 46 7B 27 FD  ..&.Ep^..7S.F.'.
00D0: E0 46 F2 89 B7 CC 42 B6   CB 28 26 6E D9 A5 C9 3A  .F....B..(&n...:
00E0: C8 41 13 60 F7 50 8C 15   AE B2 6D 1A 15 1A 57 78  .A.`.P....m...Wx
00F0: E6 92 2A D9 65 90 82 3F   6C 02 AF AE 12 3A 27 96  ..*.e..?l....:'.
0100: 36 04 D7 1D A2 80 63 A9   9B F1 E5 BA B4 7C 14 B0  6.....c.........
0110: 4E C9 B1 1F 74 5F 38 F6   51 EA 9B FA 2C A2 11 D4  N...t_8.Q...,...
0120: A9 2D 27 1A 45 B1 AF B2   4E 71 0D C0 58 46 D6 69  .-'.E...Nq..XF.i
0130: 06 CB 53 CB B3 FE 6B 41   CD 41 7E 7D 4C 0F 7C 72  ..S...kA.A..L..r
0140: 79 7A 59 CD 5E 4A 0E AC   9B A9 98 73 79 7C B4 F4  yzY.^J.....sy...
0150: CC B9 B8 07 0C B2 74 5C   B8 C7 6F 88 A1 90 A7 F4  ......t\..o.....
0160: AA F9 BF 67 3A F4 1A 15   62 1E B7 9F BE 3D B1 29  ...g:...b....=.)
0170: AF 67 A1 12 F2 58 10 19   53 03 30 1B B8 1A 89 F6  .g...X..S.0.....
0180: 9C BD 97 03 8E A3 09 F3   1D 8B 21 F1 B4 DF E4 1C  ..........!.....
0190: D1 9F 65 02 06 EA 5C D6   13 B3 84 EF A2 A5 5C 8C  ..e...\.......\.
01A0: 77 29 A7 68 C0 6B AE 40   D2 A8 B4 EA CD F0 8D 4B  w).h.k.@.......K
01B0: 38 9C 19 9A 1B 28 54 B8   89 90 EF CA 75 81 3E 1E  8....(T.....u.>.
01C0: F2 64 24 C7 18 AF 4E FF   47 9E 07 F6 35 65 A4 D3  .d$...N.G...5e..
01D0: 0A 56 FF F5 17 64 6C EF   A8 22 25 49 93 B6 DF 00  .V...dl.."%I....
01E0: 17 DA 58 7E 5D EE C5 1B   B0 D1 D1 5F 21 10 C7 F9  ..X.]......_!...
01F0: F3 BA 02 0A 27 07 C5 F1   D6 C7 D3 E0 FB 09 60 6C  ....'.........`l

]


```

`

Failure to find any match returns an exit code of -1
```
hadoop jar cloudstore-1.0.jar tlsinfo unknown 

1. TLS System Properties
========================

[001]  java.version = "11.0.18"
[002]  java.library.path = "/Users/stevel/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:."
[003]  com.sun.net.ssl.checkRevocation = (unset)
[004]  https.protocols = (unset)
[005]  javax.net.ssl.keyStore = (unset)
[006]  javax.net.ssl.keyStorePassword = (unset)
[007]  javax.net.ssl.trustStore = (unset)
[008]  javax.net.ssl.trustStorePassword = (unset)
[009]  jdk.certpath.disabledAlgorithms = (unset)
[010]  jdk.tls.client.cipherSuites = (unset)
[011]  jdk.tls.client.protocols = (unset)
[012]  jdk.tls.disabledAlgorithms = (unset)
[013]  jdk.tls.legacyAlgorithms = (unset)
[014]  jsse.enableSNIExtension = (unset)


2. HTTPS supported protocols
============================

    TLSv1.3
    TLSv1.2
    TLSv1.1
    TLSv1
    SSLv3
    SSLv2Hello

See https://www.java.com/en/configure_crypto.html


3. Certificates from the default certificate manager
====================================================

No certificates found matching the string "unknown"

2025-07-07 12:33:57,277 [main] INFO  util.ExitUtil (ExitUtil.java:terminate(241)) - Exiting with status -1: 
```