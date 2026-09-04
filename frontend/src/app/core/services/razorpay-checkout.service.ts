import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser, DOCUMENT } from '@angular/common';
import { CheckoutOrder } from '../models/content.model';

declare global {
  interface Window {
    Razorpay?: any;
  }
}

export interface CheckoutSuccess {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

const RAZORPAY_SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';

/**
 * Loads Razorpay's checkout.js on demand, the first time a payment is
 * actually initiated — not globally in index.html. That script is 57KB and
 * render-blocking; the only page on the whole site that ever needs it is the
 * invoice payment flow, so loading it for every visitor on every page was a
 * pure Lighthouse/LCP tax with no benefit.
 */
@Injectable({ providedIn: 'root' })
export class RazorpayCheckoutService {
  private platformId = inject(PLATFORM_ID);
  private document = inject(DOCUMENT);
  private scriptLoadPromise: Promise<void> | null = null;

  private loadScript(): Promise<void> {
    if (window.Razorpay) {
      return Promise.resolve();
    }

    if (!this.scriptLoadPromise) {
      this.scriptLoadPromise = new Promise((resolve, reject) => {
        const script = this.document.createElement('script');
        script.src = RAZORPAY_SCRIPT_URL;
        script.async = true;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error('Could not load the payment provider — check your connection and try again'));
        this.document.body.appendChild(script);
      });
    }

    return this.scriptLoadPromise;
  }

  async open(order: CheckoutOrder, customer: { name: string; email: string }): Promise<CheckoutSuccess> {
    if (!isPlatformBrowser(this.platformId)) {
      throw new Error('Payment checkout is only available in the browser');
    }

    await this.loadScript();

    return new Promise((resolve, reject) => {
      const rzp = new window.Razorpay({
        key: order.razorpayKeyId,
        amount: order.amountInPaise,
        currency: order.currency,
        name: 'Neelastack',
        description: order.description,
        order_id: order.razorpayOrderId,
        prefill: { name: customer.name, email: customer.email },
        theme: { color: '#F5A623' },
        handler: (response: CheckoutSuccess) => resolve(response),
        modal: {
          ondismiss: () => reject(new Error('Payment cancelled')),
        },
      });

      rzp.open();
    });
  }
}
