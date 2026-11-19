import {
  registerDecorator,
  type ValidationArguments,
  type ValidationOptions,
} from 'class-validator';

/**
 * El campo debe ser idéntico a otra propiedad del mismo DTO.
 * Uso: @Match('password') passwordConfirmation: string;  (RN-084)
 */
export function Match(property: string, options?: ValidationOptions) {
  return (target: object, propertyName: string) => {
    registerDecorator({
      name: 'match',
      target: target.constructor,
      propertyName,
      constraints: [property],
      options: {
        message: `${propertyName} debe coincidir con ${property}`,
        ...options,
      },
      validator: {
        validate(value: unknown, args: ValidationArguments) {
          const [related] = args.constraints as [string];
          return value === (args.object as Record<string, unknown>)[related];
        },
      },
    });
  };
}
