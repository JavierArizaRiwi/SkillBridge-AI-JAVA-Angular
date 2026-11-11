// Envío de correos. El proyecto aún no tiene proveedor (SMTP, Resend…): AuthService
// depende de esta clase abstracta y por ahora se le inyecta ConsoleMailService.
// Cuando haya proveedor, se añade otra implementación y se cambia el `useClass`
// en AuthModule; nada más.
export abstract class MailService {
  // Envía el enlace para restablecer la contraseña (RN-103)
  abstract sendPasswordReset(to: string, link: string): Promise<void>;
}
