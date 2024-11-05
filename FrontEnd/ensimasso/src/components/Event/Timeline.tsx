import React, { useState } from 'react';
import { ArrowDown } from 'lucide-react';
import { TimelineEvent } from './TimelineEvent.tsx';
import type { Event } from './event';

const events: Event[] = [
  {
    id: '1',
    date: 'March 15, 2024',
    time: '7:00 PM',
    title: 'Tech Innovation Summit 2024',
    place: 'Silicon Valley Convention Center',
    description: 'Join us for the biggest tech innovation summit of the year. Network with industry leaders, attend workshops, and discover the latest technological breakthroughs. The event will feature keynote speakers from leading tech companies, hands-on demonstrations of cutting-edge technologies, and exclusive networking opportunities.',
    image: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&q=80',
    attendees: 234,
    organizer: {
      name: 'TechCrunch',
      logo: 'https://images.unsplash.com/photo-1642784353782-ac2c82c0fd7c?auto=format&fit=crop&q=80&w=200&h=200',
    },
  },
  {
    id: '2',
    date: 'March 20, 2024',
    time: '2:00 PM',
    title: 'AI & Machine Learning Workshop',
    place: 'Digital Innovation Hub',
    description: 'An intensive workshop covering the fundamentals and advanced concepts of AI and Machine Learning. Perfect for developers and tech enthusiasts looking to expand their knowledge in artificial intelligence and its practical applications.',
    image: 'https://images.unsplash.com/photo-1485827404703-89b55fcc595e?auto=format&fit=crop&q=80',
    attendees: 156,
    organizer: {
      name: 'Google Developer Group',
      logo: 'https://images.unsplash.com/photo-1573804633927-bfcbcd909acd?auto=format&fit=crop&q=80&w=200&h=200',
    },
  },
  {
    id: '3',
    date: 'March 25, 2024',
    time: '10:00 AM',
    title: 'Startup Networking Breakfast',
    place: 'Metropolitan Business Center',
    description: 'Start your day with inspiring conversations and valuable connections. This networking breakfast brings together entrepreneurs, investors, and industry experts in a casual setting perfect for meaningful discussions and potential collaborations.',
    image: 'https://images.unsplash.com/photo-1543269865-cbf427effbad?auto=format&fit=crop&q=80',
    attendees: 89,
    organizer: {
      name: 'Startup Grind',
      logo: 'https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?auto=format&fit=crop&q=80&w=200&h=200',
    },
  },
  {
    id: '4',
    date: 'April 1, 2024',
    time: '6:30 PM',
    title: 'Future of Web Development',
    place: 'Tech Campus Auditorium',
    description: 'Explore the latest trends and future predictions in web development. Leading experts will discuss emerging technologies, best practices, and the evolution of web development frameworks and tools.',
    image: 'https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&q=80',
    attendees: 167,
    organizer: {
      name: 'Mozilla Developer Network',
      logo: 'https://images.unsplash.com/photo-1649180556628-9ba704115795?auto=format&fit=crop&q=80&w=200&h=200',
    },
  },
];

export function Timeline() {
  const [eventsList, setEventsList] = useState(events);

  const handleAttend = (id: string) => {
    setEventsList(events.map(event => {
      if (event.id === id) {
        return {
          ...event,
          attendees: event.attendees + 1,
        };
      }
      return event;
    }));
  };

  return (
    <div className="max-w-6xl mx-auto">
      <div className="text-center mb-16">
        <h1 className="text-4xl font-bold text-gray-900 mb-4">Upcoming Events</h1>
        <p className="text-gray-600 max-w-xl mx-auto">
          Join us at these exciting events and be part of our growing community
        </p>
        <ArrowDown className="w-6 h-6 mx-auto mt-6 text-blue-500 animate-bounce" />
      </div>
      
      <div className="relative">
        <div className="absolute left-1/2 transform -translate-x-1/2 h-full w-0.5 bg-gray-300" />
        
        <div className="space-y-24">
          {eventsList.map((event, index) => (
            <TimelineEvent 
              key={event.id} 
              event={event} 
              isLeft={index % 2 === 0}
              onAttend={handleAttend}
            />
          ))}
        </div>
      </div>
    </div>
  );
}