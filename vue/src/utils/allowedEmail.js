/** 与后端 AllowedMailbox 一致：QQ / 网易 / 谷歌系域名 */
export const ALLOWED_EMAIL_MESSAGE =
  '仅支持 QQ 邮箱（@qq.com、@foxmail.com）、网易邮箱（@163.com、@126.com、@yeah.net）或谷歌邮箱（@gmail.com）'

const ALLOWED_EMAIL_RE =
  /^[a-zA-Z0-9._%+-]+@(qq\.com|foxmail\.com|163\.com|126\.com|yeah\.net|gmail\.com|googlemail\.com)$/i

export function isAllowedMailboxEmail(email) {
  return ALLOWED_EMAIL_RE.test(String(email || '').trim())
}
