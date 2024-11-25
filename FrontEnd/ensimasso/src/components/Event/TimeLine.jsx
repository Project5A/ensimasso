import React from 'react';
import EventCard from './EventCard';
import './TimeLine.css';

function TimeLine() {
    const events = [
        {
            image: "../../assets/bde_bg.jpg",
            title: 'Event 1',
            date: '2023-10-01',
            location: 'Location 1',
            description: 'Description for Event 1',
            organizerLogo: "../../assets/bde_logo.jpg",
            organizerName: 'GALA',
        },
        {
            image: "../../assets/bdlc_bg.jpg",
            title: 'Event 2',
            date: '2023-10-02',
            location: 'Location 2',
            description: 'Description for Event 2',
            organizerLogo: "../../assets/bdlc_logo.png",
            organizerName: 'BDE',
        },
        {
            image: "../../assets/gala_bg.jpg",
            title: 'Event 3',
            date: '2023-10-03',
            location: 'Location 3',
            description: 'Description for Event 3',
            organizerLogo: "../../assets/gala_logo.jpg",
            organizerName: 'BDLC',
        },
    ];

    return (
        <div className="timeline">
            <div className="timeline-line"></div>
            {events.map((event, index) => (
                <div 
                    key={index} 
                    className={`timeline-event ${index % 2 === 0 ? 'left' : 'right'}`}
                >
                    {/* Event Card */}
                    <div className="event-card-section">
                        <EventCard event={event} />
                    </div>
                    
                    {/* Organizer Name and Logo */}
                    <div className="organizer-section">
                        <h3 className="event-organizer-name">{event.organizerName}</h3>
                    </div>

                    {/* Timeline Marker (Logo) */}
                    <div className="timeline-marker">
                        <img 
                            src={event.organizerLogo} 
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
