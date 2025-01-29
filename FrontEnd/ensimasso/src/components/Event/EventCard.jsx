// EventCard.jsx
import React from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faCheckCircle,faBookmark,
} from "@fortawesome/free-solid-svg-icons";
import "./EventCard.css";

const EventCard = ({ event }) => {
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
        <div className="event-card-buttons">
          <button
            className="event-card-button primary-button"
            aria-label="Mark as going"
          >
            <FontAwesomeIcon icon={faCheckCircle} className="icon" style={{ color: 'black' }} />
            Going
          </button>
          <div className="share">
            <button>
              <div className="svg-wrapper-1">
                <div className="svg-wrapper">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    width="24"
                    height="24"
                  >
                    <path fill="none" d="M0 0h24v24H0z"></path>
                    <path
                      fill="currentColor"
                      d="M1.946 9.315c-.522-.174-.527-.455.01-.634l19.087-6.362c.529-.176.832.12.684.638l-5.454 19.086c-.15.529-.455.547-.679.045L12 14l6-8-8 6-8.054-2.685z"
                    ></path>
                  </svg>
                </div>
              </div>
              <span>Send</span>
            </button>
          </div>
          <button
            className="event-card-button save-button"
            aria-label="Save event"
          >
            <FontAwesomeIcon icon={faBookmark} className="icon" style={{ color: 'black' }} />
            Save
          </button>
        </div>
      </div>
    </div>
  );
};

export default EventCard;