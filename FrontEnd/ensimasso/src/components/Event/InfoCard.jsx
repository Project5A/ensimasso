// InfoCard.jsx
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
            <span>{date}</span>
          </div>
          <div className="info-item">
            <FontAwesomeIcon icon={faMapMarkerAlt} className="info-icon" />
            <span>{location}</span>
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