import React, { useState, useEffect } from 'react';
import EventCard from './EventCard';
import { Elements } from '@stripe/react-stripe-js';
import { stripePromise } from '../Payment/stripePromise';
import InfoCard from './InfoCard';
import './TimeLine.css';

function TimeLine() {
    const [events, setEvents] = useState([]);

    useEffect(() => {
        // Corrected backend URL with port 8080
        fetch('http://localhost:8080/api/events')
            .then(response => response.json())
            .then(data => setEvents(data))
            .catch(error => console.error("Error fetching events:", error));
    }, []);

    return (
        <div className="timeline">
            <h2 className="timeline-title">Upcoming Events</h2>
            <div className="timeline-arrow"></div>
            <div className="timeline-line"></div>
            {events.map((event, index) => (
                <div 
                    key={event.id} 
                    className={`timeline-event ${index % 2 === 0 ? 'left' : 'right'}`}
                >
                    <div className="event-card-section">
                        <Elements stripe={stripePromise}>
                            <EventCard event={event} />
                        </Elements>
                    </div>
                    
                    <div className="organizer-section">
                        <InfoCard 
                            organizerName={event.organizerName}
                            date={event.date}
                            location={event.location}
                            description={event.description}
                        />
                    </div>

                    <div className="timeline-marker">
                        <img 
                            src={event.organizerPhoto} 
                            className="event-logo" 
                            alt={event.organizerName}
                        />
                    </div>
                </div>
            ))}
        </div>
    );
}

export default TimeLine;
