import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { MailService } from './mail.service.js';

// "Envía" los correos escribiéndolos en el log. En desarrollo y en los tests
// permite copiar el enlace de recuperación. En producción NO escribe el enlace
// (quien lea los logs podría usarlo): solo avisa de que falta un proveedor.
@Injectable()
export class ConsoleMailService extends MailService {
  private readonly logger = new Logger('Mail');

  constructor(private readonly config: ConfigService) {
    super();
  }

  sendPasswordReset(to: string, link: string): Promise<void> {
    if (this.config.get<string>('app.env') === 'production') {
      this.logger.warn(
        `No hay proveedor de correo configurado: no se envió la recuperación de contraseña a ${to}`,
      );
    } else {
      this.logger.log(`Recuperación de contraseña para ${to}: ${link}`);
    }
    return Promise.resolve();
  }
}
