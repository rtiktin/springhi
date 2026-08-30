import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef } from 'react';
import { loadStripe, type Stripe, type StripeElements, type StripePaymentElement } from '@stripe/stripe-js';

export interface StripeCardInputHandle {
    /** Confirm the SetupIntent; resolves to the created payment method id, or rejects with an Error. */
    confirm: () => Promise<string>;
}

interface Props {
    publishableKey: string;
    clientSecret: string;
}

/**
 * Embedded Stripe PaymentElement (card collection) using vanilla stripe-js.
 * The parent triggers confirmation via the imperative `confirm()` handle so the
 * existing "Subscribe" / "Save Card" button stays the single submit point.
 */
const StripeCardInput = forwardRef<StripeCardInputHandle, Props>(({ publishableKey, clientSecret }, ref) => {
    const stripePromise = useMemo(() => loadStripe(publishableKey), [publishableKey]);
    const mountRef = useRef<HTMLDivElement | null>(null);
    const stripeRef = useRef<Stripe | null>(null);
    const elementsRef = useRef<StripeElements | null>(null);
    const elementRef = useRef<StripePaymentElement | null>(null);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            const stripe = await stripePromise;
            if (cancelled || !stripe || !mountRef.current) return;
            const elements = stripe.elements({ appearance: { theme: 'night', variables: { colorPrimary: '#6c47ff' } }, clientSecret });
            const card = elements.create('payment', { fields: { billingDetails: 'never' } });
            card.mount(mountRef.current);
            stripeRef.current = stripe;
            elementsRef.current = elements;
            elementRef.current = card;
        })();
        return () => {
            cancelled = true;
            elementRef.current?.unmount();
            elementRef.current?.destroy();
            elementRef.current = null;
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
