import React from 'react';
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faCalendarAlt,
  faMapMarkerAlt,
  faInfoCircle,
  faUser,
} from "@fortawesome/free-solid-svg-icons";
import './InfoCard.css';

const InfoCard = ({ organizerName, date, location, description }) => {
  // Format the date to match Google Calendar's format (YYYYMMDDTHHMMSSZ)
  const formattedDate = new Date(date).toISOString().replace(/-|:|\.\d+/g, "");

  // Google Maps URL that uses the location string and will open directions from the user's current location
  const googleMapsUrl = `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(location)}`;

  // Google Calendar event URL with pre-filled details
  const googleCalendarUrl = `https://www.google.com/calendar/render?action=TEMPLATE&text=${encodeURIComponent(organizerName)}&dates=${formattedDate}/${formattedDate}&location=${encodeURIComponent(location)}&details=${encodeURIComponent(description)}&sf=true&output=xml`;

  return (
    <div className="outer">
      <div className="dot"></div>
      <div className="card">
        <div className="ray"></div>
        <div className="organizer-header">
          <div className="text">{organizerName}</div>
        </div>
        
        <div className="info-content">
          <div className="info-item">
            <FontAwesomeIcon icon={faCalendarAlt} className="info-icon" />
            {/* Make the date clickable and direct to Google Calendar */}
            <a href={googleCalendarUrl} target="_blank" rel="noopener noreferrer">
              <span>{date}</span>
            </a>
          </div>
          <div className="info-item">
            <FontAwesomeIcon icon={faMapMarkerAlt} className="info-icon" />
            {/* Make the location clickable and direct to Google Maps */}
            <a href={googleMapsUrl} target="_blank" rel="noopener noreferrer">
              <span>{location}</span>
            </a>
          </div>
          <div className="info-item description">
            <FontAwesomeIcon icon={faInfoCircle} className="info-icon" />
            <span>{description}</span>
          </div>
        </div>

        <div className="line topl"></div>
        <div className="line leftl"></div>
        <div className="line bottoml"></div>
        <div className="line rightl"></div>
      </div>
    </div>
  );
};

export default InfoCard;
