import React, { useState, useEffect } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faCheckCircle, faBookmark } from "@fortawesome/free-solid-svg-icons";
import { faFacebook, faTwitter, faWhatsapp } from "@fortawesome/free-brands-svg-icons";
import { useUser } from "../../contexts/UserContext";
import Modal from "react-modal";
import PaymentForm from "../Payment/PaymentForm"; 
import "./EventCard.css";

Modal.setAppElement('#root');

const EventCard = ({ event }) => {
  const { user } = useUser();
  const [showShareOptions, setShowShareOptions] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [paymentPrice, setPaymentPrice] = useState(event.nonAdhPrice);

  useEffect(() => {
    const checkMembership = async () => {
      if (user && user.role === "GUEST" && event.organizerId) {
        try {
          const response = await fetch(`http://localhost:8080/api/guests/${user.id}/adhesions`);
          const memberships = await response.json();
          const isMember = memberships.some(
            (asso) => asso && asso.id && Number(asso.id) === Number(event.organizerId)
          );
          if (isMember) {
            setPaymentPrice(event.adhPrice);
          }
        } catch (err) {
          console.error("Erreur lors de la vérification des adhésions :", err);
        }
      }
    };

    checkMembership();
  }, [user, event.organizerId, event.adhPrice, event.nonAdhPrice]);

  const shareEvent = (platform) => {
    const eventTitle = encodeURIComponent(event.title);
    const eventDescription = encodeURIComponent(event.description);
    const eventUrl = encodeURIComponent(window.location.href);
    
    const socialMediaUrls = {
      facebook: `https://www.facebook.com/sharer/sharer.php?u=${eventUrl}`,
      twitter: `https://twitter.com/intent/tweet?text=${eventTitle}&url=${eventUrl}`,
      whatsapp: `https://wa.me/?text=${eventTitle} - ${eventDescription} ${eventUrl}`,
    };

    window.open(socialMediaUrls[platform], "_blank");
    setShowShareOptions(false);
  };

  const handleGoingClick = () => {
    if (paymentPrice === 0) {
      handlePaymentSuccess("free"); // Simuler un paiement réussi
    } else {
      setShowPaymentModal(true);
    }
  };
  
  const handlePaymentSuccess = (paymentIntent) => {
    console.log("Paiement réussi pour l'événement", event.id, paymentIntent);
    alert("Votre participation a été validée avec succès !");
    setShowPaymentModal(false);
  };

  return (
    <div className="event-card">
      <img src={event.eventImage} alt={event.title} className="event-card-image" />
      <div className="event-card-content">
        <h2 className="event-card-title">{event.title}</h2>
        <div className="event-card-buttons">
          <button
            className="event-card-button primary-button"
            aria-label="Mark as going"
            onClick={handleGoingClick}
          >
            <FontAwesomeIcon icon={faCheckCircle} className="icon" style={{ color: 'black' }} />
            Going
          </button>
          <div className="share">
            <button onClick={() => setShowShareOptions(!showShareOptions)}>
              <div className="svg-wrapper-1">
                <div className="svg-wrapper">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
                    <path fill="none" d="M0 0h24v24H0z"></path>
                    <path fill="currentColor" d="M1.946 9.315c-.522-.174-.527-.455.01-.634l19.087-6.362c.529-.176.832.12.684.638l-5.454 19.086c-.15.529-.455.547-.679.045L12 14l6-8-8 6-8.054-2.685z"></path>
                  </svg>
                </div>
              </div>
              <span>Send</span>
            </button>
            {showShareOptions && (
              <div className="social-share-options">
                <div className="social-bubble facebook" onClick={() => shareEvent("facebook")} aria-label="Share on Facebook">
                  <FontAwesomeIcon icon={faFacebook} size="2x" />
                </div>
                <div className="social-bubble twitter" onClick={() => shareEvent("twitter")} aria-label="Share on Twitter">
                  <FontAwesomeIcon icon={faTwitter} size="2x" />
                </div>
                <div className="social-bubble whatsapp" onClick={() => shareEvent("whatsapp")} aria-label="Share on WhatsApp">
                  <FontAwesomeIcon icon={faWhatsapp} size="2x" />
                </div>
              </div>
            )}
          </div>
          <button className="event-card-button save-button" aria-label="Save event">
            <FontAwesomeIcon icon={faBookmark} className="icon" style={{ color: 'black' }} />
            Save
          </button>
        </div>
      </div>
      <Modal
        isOpen={showPaymentModal}
        onRequestClose={() => setShowPaymentModal(false)}
        className="payment-modal"
        overlayClassName="payment-overlay"
      >
        <PaymentForm
          amount={paymentPrice * 100}
          onPaymentSuccess={handlePaymentSuccess}
          onCancel={() => setShowPaymentModal(false)}
        />
      </Modal>
    </div>
  );
};

export default EventCard;
