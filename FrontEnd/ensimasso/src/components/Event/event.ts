export interface Event {
    id: string;
    date: string;
    time: string;
    title: string;
    place: string;
    description: string;
    image: string;
    attendees: number;
    organizer: {
      name: string;
      logo: string;
    };
  }