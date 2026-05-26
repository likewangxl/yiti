import CryptoJS from 'crypto-js';

const AES_KEY = CryptoJS.enc.Utf8.parse('yiti-sql-probe-k'); // 16 字节，与后端一致

/**
 * AES-128-ECB 加密 SQL → Base64 字符串
 */
export function encryptSql(sql) {
  const encrypted = CryptoJS.AES.encrypt(sql, AES_KEY, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7
  });
  return encrypted.toString(); // Base64
}

/**
 * Base64 → AES-128-ECB 解密 SQL
 */
export function decryptSql(cipherText) {
  const decrypted = CryptoJS.AES.decrypt(cipherText, AES_KEY, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7
  });
  return decrypted.toString(CryptoJS.enc.Utf8);
}
