import React, { useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faCalendarAlt,
  faMapMarkerAlt,
  faCheckCircle,
  faShareAlt,
  faChevronDown,
  faChevronUp,
} from "@fortawesome/free-solid-svg-icons";
import "./EventCard.css";

const EventCard = ({ event }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const toggleDescription = () => setIsExpanded((prev) => !prev);

  return (
    <div
      className="event-card"
      role="article"
      aria-label={`Event card for ${event.title}`}
    >
      <img
        src={event.image}
        alt={`${event.title}`}
        className="event-card-image"
      />
      <div className="event-card-content">
        <h2 className="event-card-title">{event.title}</h2>
        <p className="event-card-date">
          <FontAwesomeIcon icon={faCalendarAlt} className="icon" />
          {event.date}
        </p>
        <p className="event-card-location">
          <FontAwesomeIcon icon={faMapMarkerAlt} className="icon" />
          {event.location}
        </p>
        <div className="event-card-buttons">
          <button
            className="event-card-button primary-button"
            aria-label="Mark as going"
          >
            <FontAwesomeIcon icon={faCheckCircle} className="icon" />
            Going
          </button>
          <button
            className="event-card-button secondary-button"
            aria-label="Share event"
          >
            <FontAwesomeIcon icon={faShareAlt} className="icon" />
            Share
          </button>
          <button
            className="event-card-button toggle-button"
            onClick={toggleDescription}
            aria-expanded={isExpanded}
          >
            <FontAwesomeIcon
              icon={isExpanded ? faChevronUp : faChevronDown}
              className="icon"
            />
            {isExpanded ? "See Less" : "See More"}
          </button>
        </div>
      </div>
      {isExpanded && (
        <div className="event-card-description">
          <p>{event.description}</p>
        </div>
      )}
    </div>
  );
};

export default EventCard;
