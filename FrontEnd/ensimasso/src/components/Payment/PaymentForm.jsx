import React, { useState } from 'react';
import { CardElement, useStripe, useElements } from '@stripe/react-stripe-js';
import axios from 'axios';

const PaymentForm = ({ amount, onPaymentSuccess, onCancel }) => {
  const stripe = useStripe();
  const elements = useElements();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const { data } = await axios.post('http://localhost:8080/api/payment/create-payment-intent', {
        amount: amount, // montant en centimes
        currency: 'eur'
      });
      const clientSecret = data.clientSecret;

      const result = await stripe.confirmCardPayment(clientSecret, {
        payment_method: {
          card: elements.getElement(CardElement),
        },
      });

      if (result.error) {
        setError(result.error.message);
      } else if (result.paymentIntent && result.paymentIntent.status === 'succeeded') {
        onPaymentSuccess(result.paymentIntent);
      }
    } catch (err) {
      setError('Erreur lors du paiement.');
      console.error(err);
    }
    setLoading(false);
  };

  return (
    <form onSubmit={handleSubmit} className="p-4 bg-white rounded shadow-md">
      {/* Affichage du montant à payer */}
      <p className="text-xl font-bold mb-4">
        Montant à payer : {(amount / 100).toFixed(2)} €
      </p>
      <CardElement options={{ hidePostalCode: true }} />
      {error && <div className="text-red-500 mt-2">{error}</div>}
      <div className="mt-4 flex justify-between">
        <button type="button" onClick={onCancel} className="px-4 py-2 bg-gray-300 rounded">
          Annuler
        </button>
        <button type="submit" disabled={!stripe || loading} className="px-4 py-2 bg-blue-500 text-white rounded">
          {loading ? "Paiement..." : "Payer"}
        </button>
      </div>
    </form>
  );
};

export default PaymentForm;
