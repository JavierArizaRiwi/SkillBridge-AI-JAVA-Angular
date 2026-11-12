import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../../core/api';

@Component({
  selector: 'app-payment-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payment-modal.html'
})
export class PaymentModal {
  @Input() bookingId!: string;
  @Input() amount: number = 0;
  
  @Output() close = new EventEmitter<void>();
  @Output() paymentSuccess = new EventEmitter<void>();

  cardData = {
    cardNumber: '',
    expiryDate: '',
    cvv: ''
  };

  loading = false;
  errorMsg = '';

  constructor(private http: HttpClient) {}

  formatCardNumber(value: string) {
    // Si el usuario escribe una letra u otro caracter no válido, evitamos que Angular lo repinte con espacios que validen patrones parciales.
    // Solo formateamos los numeros. Si hay letras, las respetamos para que el HTML Pattern se dispare y ponga la caja roja.
    const isOnlyNumbersAndSpaces = /^[0-9\s]*$/.test(value);

    let input = value.replace(/\s/g, ''); // quitamos espacios para procesar la cadena cruda
    input = input.substring(0, 16);

    if (isOnlyNumbersAndSpaces) {
      let formatted = '';
      for (let i = 0; i < input.length; i++) {
        if (i > 0 && i % 4 === 0) {
          formatted += ' ';
        }
        formatted += input[i];
      }
      setTimeout(() => this.cardData.cardNumber = formatted);
    } else {
      setTimeout(() => this.cardData.cardNumber = value.substring(0, 19));
    }
  }

  formatExpiry(value: string) {
    const isOnlyNumbersAndSlash = /^[0-9\/]*$/.test(value);
    
    let input = value.replace(/\//g, '');
    input = input.substring(0, 4);

    if (isOnlyNumbersAndSlash) {
      if (input.length > 2) {
        input = input.substring(0, 2) + '/' + input.substring(2);
      }
      setTimeout(() => this.cardData.expiryDate = input);
    } else {
      setTimeout(() => this.cardData.expiryDate = value.substring(0, 5));
    }
  }

  formatCvv(value: string) {
    let input = value.substring(0, 3);
    setTimeout(() => this.cardData.cvv = input);
  }

  // Valida lógica de negocio de la tarjeta y envía el payload a la pasarela (backend)
  submitPayment() {
    this.loading = true;
    this.errorMsg = '';

    // Validar fecha de expiración lógicamente
    if (this.cardData.expiryDate.length === 5) {
      const parts = this.cardData.expiryDate.split('/');
      const month = parseInt(parts[0], 10);
      const year = parseInt('20' + parts[1], 10);
      const now = new Date();
      const currentYear = now.getFullYear();
      const currentMonth = now.getMonth() + 1;

      if (month < 1 || month > 12) {
        this.errorMsg = 'El mes de vencimiento es inválido.';
        this.loading = false;
        return;
      }

      if (year < currentYear || (year === currentYear && month < currentMonth)) {
        this.errorMsg = 'La tarjeta ingresada está vencida.';
        this.loading = false;
        return;
      }
    }

        if (this.cardData.cvv === '000') {
      setTimeout(() => {
        this.errorMsg = "Pago rechazado: Por políticas de seguridad, los códigos de verificación (CVV) con valor '000' no son admitidos.";
        this.loading = false;
      }, 600);
      return;
    }

    const idempotencyKey = crypto.randomUUID();

    const payload = {
      bookingId: this.bookingId,
      amount: this.amount,
      cardNumber: this.cardData.cardNumber.replace(/\s/g, ''),
      expiryDate: this.cardData.expiryDate,
      cvv: this.cardData.cvv
    };

    this.http.post<any>(apiBase() + '/payments/authorize', payload, {
      headers: {
        'Idempotency-Key': idempotencyKey
      }
    }).subscribe({
      next: (response) => {
        this.loading = false;
        if (response['status'] === 'APPROVED') {
          this.paymentSuccess.emit();
        } else {
          let msg = response['message'];
          if (msg === 'Card declined deterministically' || this.cardData.cardNumber.endsWith('0000')) {
            this.errorMsg = "Pago rechazado: Por políticas de seguridad, las tarjetas con terminación '0000' no están habilitadas para realizar transacciones.";
          } else {
            this.errorMsg = 'Pago rechazado: ' + msg + '. Intenta con otra tarjeta.';
          }
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = 'Ocurrió un error técnico procesando el pago. Por favor intenta de nuevo.';
        console.error(err);
      }
    });
  }
}

