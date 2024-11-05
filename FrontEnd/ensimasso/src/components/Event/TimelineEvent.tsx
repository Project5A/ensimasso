import React, { useState } from 'react';
import { Circle, Share2, Users, ChevronDown, ChevronUp, MapPin, Clock } from 'lucide-react';
import type { Event } from './event';

interface TimelineEventProps {
  event: Event;
  isLeft: boolean;
  onAttend: (id: string) => void;
}

export function TimelineEvent({ event, isLeft, onAttend }: TimelineEventProps) {
  const [isExpanded, setIsExpanded] = useState(false);
  const [isAttending, setIsAttending] = useState(false);

  const handleShare = () => {
    if (navigator.share) {
      navigator.share({
        title: event.title,
        text: `Check out this event: ${event.title}`,
        url: window.location.href,
      }).catch(console.error);
    }
  };

  const handleAttend = () => {
    setIsAttending(!isAttending);
    onAttend(event.id);
  };

  const OrganizerLogo = () => (
    <div className={`w-5/12 flex ${isLeft ? 'justify-start pl-4' : 'justify-end pr-4'}`}>
      <div className="relative group">
        <div className="
          bg-white/80 backdrop-blur-lg rounded-xl p-4 shadow-lg 
          transform hover:-translate-y-1 transition-all duration-300
          border border-white/20 hover:shadow-xl
        ">
          <div className="flex flex-col items-center">
            <div className="relative">
              <div className="w-20 h-20 rounded-full overflow-hidden ring-4 ring-white shadow-lg">
                <img 
                  src={event.organizer.logo} 
                  alt={`${event.organizer.name} logo`}
                  className="w-full h-full object-cover"
                />
              </div>
              {/* Comment Bubble Triangle */}
              <div className="
                absolute -bottom-2 left-1/2 transform -translate-x-1/2
                w-4 h-4 rotate-45
                bg-white/80 backdrop-blur-lg
                border-r border-b border-white/20
              "/>
            </div>
            
            {/* Comment Bubble */}
            <div className="
              mt-4 p-3 rounded-xl bg-white/80 backdrop-blur-lg
              border border-white/20 shadow-lg
              transform transition-all duration-300
              w-full
            ">
              <span className="text-sm font-medium text-gray-800 block text-center">
                {event.organizer.name}
              </span>
              <span className="text-xs text-gray-600 block text-center mt-1">
                Event Organizer
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className={`flex items-center justify-center ${isLeft ? 'flex-row-reverse' : ''} relative`}>
      <div className={`w-5/12 ${isLeft ? 'text-right' : 'text-left'}`}>
        <div
          className={`
            rounded-xl backdrop-blur-lg transition-all duration-300
            bg-white/80 overflow-hidden transform hover:-translate-y-1
            border border-white/20 shadow-lg hover:shadow-xl
            ${isLeft ? 'ml-auto mr-4' : 'mr-auto ml-4'}
          `}
        >
          {/* Image Section */}
          <div className="relative h-48 overflow-hidden rounded-t-xl">
            <img 
              src={event.image} 
              alt={event.title}
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
          </div>

          {/* Content Section */}
          <div className="p-6 backdrop-blur-lg bg-white/50">
            <div className="flex items-center gap-2 text-sm text-blue-600 mb-2">
              <Clock className="w-4 h-4" />
              <span>{event.time}</span>
              <span>•</span>
              <span>{event.date}</span>
            </div>

            <h3 className="text-xl font-bold mb-2 text-gray-900">{event.title}</h3>
            
            <div className="flex items-center gap-2 text-gray-600 mb-4">
              <MapPin className="w-4 h-4" />
              <span>{event.place}</span>
            </div>

            {/* Buttons */}
            <div className="flex items-center gap-3 mt-4">
              <button
                onClick={handleAttend}
                className={`
                  flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium
                  backdrop-blur-sm border border-white/20 shadow-sm
                  ${isAttending 
                    ? 'bg-green-100/80 text-green-700 hover:bg-green-200/80' 
                    : 'bg-blue-100/80 text-blue-700 hover:bg-blue-200/80'
                  }
                  transition-colors duration-200
                `}
              >
                <Users className="w-4 h-4" />
                {isAttending ? 'Attending' : 'Attend'}
              </button>

              <button
                onClick={handleShare}
                className="flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium
                  bg-gray-100/80 text-gray-700 hover:bg-gray-200/80
                  backdrop-blur-sm border border-white/20 shadow-sm
                  transition-colors duration-200"
              >
                <Share2 className="w-4 h-4" />
                Share
              </button>

              <button
                onClick={() => setIsExpanded(!isExpanded)}
                className="flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium
                  bg-gray-100/80 text-gray-700 hover:bg-gray-200/80
                  backdrop-blur-sm border border-white/20 shadow-sm
                  transition-colors duration-200"
              >
                {isExpanded ? (
                  <ChevronUp className="w-4 h-4" />
                ) : (
                  <ChevronDown className="w-4 h-4" />
                )}
                {isExpanded ? 'Less' : 'More'}
              </button>
            </div>

            {/* Expandable Description */}
            <div
              className={`
                mt-4 text-gray-700 overflow-hidden transition-all duration-300
                ${isExpanded ? 'max-h-96' : 'max-h-0'}
              `}
            >
              <p className="text-sm">{event.description}</p>
              
              <div className="mt-4 flex items-center gap-2 text-sm text-gray-500">
                <Users className="w-4 h-4" />
                <span>{event.attendees} people attending</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="z-10">
        <div className="w-8 h-8 rounded-full bg-blue-500/20 backdrop-blur-sm flex items-center justify-center">
          <Circle className="w-6 h-6 text-blue-500" />
        </div>
      </div>

      <OrganizerLogo />
    </div>
  );
}