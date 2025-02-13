import React, { useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faCheckCircle, faBookmark } from "@fortawesome/free-solid-svg-icons";
import { faFacebook, faTwitter, faWhatsapp } from "@fortawesome/free-brands-svg-icons";
import "./EventCard.css";

const EventCard = ({ event }) => {
  const [showShareOptions, setShowShareOptions] = useState(false);

  // Function to share event on selected social media
  const shareEvent = (platform) => {
    const eventTitle = encodeURIComponent(event.title);
    const eventDescription = encodeURIComponent(event.description);
    const eventUrl = encodeURIComponent(window.location.href);
    
    const socialMediaUrls = {
      facebook: `https://www.facebook.com/sharer/sharer.php?u=${eventUrl}`,
      twitter: `https://twitter.com/intent/tweet?text=${eventTitle}&url=${eventUrl}`,
      whatsapp: `https://wa.me/?text=${eventTitle} - ${eventDescription} ${eventUrl}`,
    };

    window.open(socialMediaUrls[platform], '_blank');
    setShowShareOptions(false);
  };

  return (
    <div className="event-card" role="article" aria-label={`Event card for ${event.title}`}>
      {/* Use event.eventImage instead of event.image */}
      <img
        src={event.eventImage}
        alt={`${event.title}`}
        className="event-card-image"
      />
      <div className="event-card-content">
        <h2 className="event-card-title">{event.title}</h2>
        <div className="event-card-buttons">
          <button className="event-card-button primary-button" aria-label="Mark as going">
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

            {/* Conditional rendering for share options */}
            {showShareOptions && (
              <div className="social-share-options">
                <div className="social-bubble facebook" onClick={() => shareEvent('facebook')} aria-label="Share on Facebook">
                  <FontAwesomeIcon icon={faFacebook} size="2x" />
                </div>
                <div className="social-bubble twitter" onClick={() => shareEvent('twitter')} aria-label="Share on Twitter">
                  <FontAwesomeIcon icon={faTwitter} size="2x" />
                </div>
                <div className="social-bubble whatsapp" onClick={() => shareEvent('whatsapp')} aria-label="Share on WhatsApp">
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
    </div>
  );
};

export default EventCard;
