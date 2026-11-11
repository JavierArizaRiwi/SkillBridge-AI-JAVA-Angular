import { SetMetadata } from '@nestjs/common';

export const IS_PUBLIC_KEY = 'isPublic';

/**
 * Excluye un controlador o una ruta del JWT obligatorio (RN-094).
 * Todo lo que no lleve @Public() exige un access token válido.
 */
export const Public = () => SetMetadata(IS_PUBLIC_KEY, true);
