// Roles del sistema (HU-017). Un usuario tiene uno solo; las cuentas creadas
// desde el registro público son siempre CUSTOMER.
export enum UserRole {
  ADMIN = 'ADMIN',
  WAITER = 'WAITER',
  KITCHEN = 'KITCHEN',
  CUSTOMER = 'CUSTOMER',
}
