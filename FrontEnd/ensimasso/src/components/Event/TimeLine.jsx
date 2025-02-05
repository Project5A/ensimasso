// TimeLine.jsx
import React from 'react';
import EventCard from './EventCard';
import InfoCard from './InfoCard';
import './TimeLine.css';

function TimeLine() {
    const events = [
        {
            image: "../../assets/bde_bg.jpg",
            title: 'Event 1',
            date: '2023-10-01',
            location: 'ENSIM LE MANS',
            description: 'Description for Event 1',
            organizerLogo: "../../assets/bde_logo.jpg",
            organizerName: 'GALA',
        },
        {
            image: "../../assets/bdlc_bg.jpg",
            title: 'Event 2',
            date: '2023-10-02',
            location: 'BLUE ZINC, LE MANS',
            description: 'Description for Event 2',
            organizerLogo: "../../assets/bdlc_logo.png",
            organizerName: 'BDE',
        },
        {
            image: "../../assets/gala_bg.jpg",
            title: 'Event 3',
            date: '2023-10-03',
            location: 'LE LODGE PUB, LE MANS',
            description: 'Description for Event 3',
            organizerLogo: "../../assets/gala_logo.jpg",
            organizerName: 'BDLC',
        },
    ];

    return (
        <div className="timeline">
            <h2 className="timeline-title">Upcoming Events</h2>
            <div className="timeline-arrow"></div>
            <div className="timeline-line"></div>
            {events.map((event, index) => (
                <div 
                    key={index} 
                    className={`timeline-event ${index % 2 === 0 ? 'left' : 'right'}`}
                >
                    <div className="event-card-section">
                        <EventCard event={event} />
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