import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef } from 'react';
import { loadStripe, type Stripe, type StripeElements, type StripePaymentElement } from '@stripe/stripe-js';

export interface StripeCardInputHandle {
    /** Confirm the SetupIntent; resolves to the created payment method id, or rejects with an Error. */
    confirm: () => Promise<string>;
}

interface BillingDefaults {
    name?: string;
    email?: string;
    phone?: string;
}

interface Props {
    publishableKey: string;
    clientSecret: string;
    /** Account-derived defaults (name/email/phone) used to prefill the PaymentElement's
     *  billing-details fields. */
    defaultBillingDetails?: BillingDefaults;
}

/**
 * Embedded Stripe card collection via the PaymentElement. The available payment methods are
 * driven entirely by the SetupIntent's payment_method_types (created server-side): a card-only
 * intent renders a card-only form (no Stripe Link), so in DEV/test mode — where the backend
 * builds a card-only SetupIntent — Link never auto-fills a browser-saved card across users.
 * In live mode the backend adds `link` to the intent when the admin toggle is on, and the
 * PaymentElement surfaces Link automatically. This avoids the Card Element's `disableLink`
 * option, which breaks input interactivity in this stripe-js/Edge setup.
 * The parent triggers confirmation via the imperative {@code confirm()} handle so the existing
 * "Subscribe" / "Save Card" button stays the single submit point.
 */
const StripeCardInput = forwardRef<StripeCardInputHandle, Props>(({ publishableKey, clientSecret, defaultBillingDetails }, ref) => {
    const stripePromise = useMemo(() => loadStripe(publishableKey), [publishableKey]);
    const mountRef = useRef<HTMLDivElement | null>(null);
    const stripeRef = useRef<Stripe | null>(null);
    const elementsRef = useRef<StripeElements | null>(null);
    const paymentElRef = useRef<StripePaymentElement | null>(null);
    // Defaults are fixed for the life of a SetupIntent (set before mount); keep them in a ref so the
    // mount effect doesn't re-run (which would recreate/destroy the Element) if the parent re-renders.
    const billingRef = useRef<BillingDefaults | undefined>(defaultBillingDetails);
    billingRef.current = defaultBillingDetails;

    useEffect(() => {
        let cancelled = false;
        (async () => {
            const stripe = await stripePromise;
            if (cancelled || !stripe || !mountRef.current) return;
            // Intent-bound Elements group: the PaymentElement reads its allowed payment methods from
            // the SetupIntent (clientSecret). Card-only intent → card-only form (no Link).
            const appearance = { theme: 'night' as const, variables: { colorPrimary: '#6c47ff' } };
            const elements = stripe.elements({ appearance, clientSecret });
            const bd = billingRef.current;
            const defaults: { name?: string; email?: string; phone?: string } = {};
            if (bd?.name?.trim()) defaults.name = bd.name.trim();
            if (bd?.email?.trim()) defaults.email = bd.email.trim();
            if (bd?.phone?.trim()) defaults.phone = bd.phone.trim();
            const payment = elements.create('payment', {
                fields: { billingDetails: 'auto' },
                defaultValues: Object.keys(defaults).length ? { billingDetails: defaults } : undefined,
            });
            payment.mount(mountRef.current);
            paymentElRef.current = payment;
            stripeRef.current = stripe;
            elementsRef.current = elements;
        })();
        return () => {
            cancelled = true;
            paymentElRef.current?.unmount();
            paymentElRef.current?.destroy();
            paymentElRef.current = null;
            elementsRef.current = null;
            stripeRef.current = null;
        };
    }, [stripePromise, clientSecret]);

    useImperativeHandle(ref, () => ({
        confirm: async () => {
            const stripe = stripeRef.current;
            const elements = elementsRef.current;
            if (!stripe || !elements) {
                throw new Error('Card input is not ready yet.');
            }
            const { error, setupIntent } = await stripe.confirmSetup({
                elements,
                redirect: 'if_required',
            });
            if (error) {
                throw new Error(error.message ?? 'Card verification failed.');
            }
            const pmId = setupIntent?.payment_method;
            if (typeof pmId !== 'string') {
                throw new Error('No payment method returned from card verification.');
            }
            return pmId;
        },
    }));

    return <div ref={mountRef} style={{ minHeight: 56 }} />;
});

StripeCardInput.displayName = 'StripeCardInput';
export default StripeCardInput;
