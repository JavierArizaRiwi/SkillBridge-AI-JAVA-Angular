import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { ApiBody, ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { UserResponseDto } from '../users/dto/index.js';
import { AuthService } from './auth.service.js';
import { Public } from './decorators/index.js';
import {
  ForgotPasswordDto,
  LoginDto,
  LoginResponseDto,
  MessageResponseDto,
  RegisterDto,
  ResetPasswordDto,
} from './dto/index.js';

@ApiTags('Auth')
@Public()
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  // POST /api/v1/auth/register
  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({
    summary: 'Registrar un cliente',
    description:
      'Crea una cuenta con rol CUSTOMER y estado ACTIVE. La contraseña se guarda cifrada y nunca se devuelve.',
  })
  @ApiResponse({
    status: 201,
    description: 'Cuenta creada',
    type: UserResponseDto,
  })
  @ApiResponse({
    status: 400,
    description:
      'Datos inválidos: email, teléfono, contraseña débil (RN-083) o confirmación distinta (RN-084)',
  })
  @ApiResponse({
    status: 409,
    description: 'Ya existe una cuenta con ese correo (RN-082)',
  })
  register(@Body() dto: RegisterDto): Promise<UserResponseDto> {
    return this.authService.register(dto);
  }

  // POST /api/v1/auth/login
  @Post('login')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Iniciar sesión',
    description:
      'Devuelve un access token JWT para enviar como "Authorization: Bearer <token>". Su vida la fija JWT_EXPIRES_IN.',
  })
  @ApiBody({
    type: LoginDto,
    examples: {
      cliente: {
        value: { email: 'carlos@example.com', password: 'Password123' },
      },
    },
  })
  @ApiResponse({
    status: 200,
    description: 'Credenciales válidas',
    type: LoginResponseDto,
  })
  @ApiResponse({ status: 400, description: 'Email o contraseña ausentes' })
  @ApiResponse({
    status: 401,
    description:
      'Credenciales inválidas o cuenta inactiva; el mensaje no indica cuál (RN-091)',
  })
  login(@Body() dto: LoginDto): Promise<LoginResponseDto> {
    return this.authService.login(dto);
  }

  // POST /api/v1/auth/forgot-password
  @Post('forgot-password')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Solicitar la recuperación de contraseña',
    description:
      'Si la cuenta existe y está activa, envía un enlace con un token temporal de un solo uso. Responde siempre lo mismo, exista o no el correo.',
  })
  @ApiResponse({
    status: 200,
    description: 'Solicitud recibida (mensaje genérico)',
    type: MessageResponseDto,
  })
  @ApiResponse({ status: 400, description: 'Correo con formato inválido' })
  forgotPassword(@Body() dto: ForgotPasswordDto): Promise<MessageResponseDto> {
    return this.authService.forgotPassword(dto);
  }

  // POST /api/v1/auth/reset-password
  @Post('reset-password')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Restablecer la contraseña con el token recibido',
    description:
      'El token debe estar vigente y sin usar; al usarlo deja de servir (RN-103, RN-104).',
  })
  @ApiResponse({
    status: 200,
    description: 'Contraseña actualizada',
    type: MessageResponseDto,
  })
  @ApiResponse({
    status: 400,
    description:
      'Token inválido, expirado o ya usado (mismo mensaje en los tres casos), contraseña débil (RN-105) o confirmación distinta',
  })
  resetPassword(@Body() dto: ResetPasswordDto): Promise<MessageResponseDto> {
    return this.authService.resetPassword(dto);
  }
}
