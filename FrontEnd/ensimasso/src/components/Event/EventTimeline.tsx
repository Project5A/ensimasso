'use client'
import React, { useState, useRef, useEffect } from 'react'
import Image from 'next/image'
import { motion, AnimatePresence, useAnimation } from 'framer-motion'
import { Calendar, Clock, MapPin, ChevronRight, X, Share2, Plus, Navigation, Ticket } from 'lucide-react'
import { Button } from "./button.tsx"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogClose, DialogFooter } from "./dialog.tsx"
import { Card, CardContent, CardHeader, CardTitle, CardDescription, CardFooter } from "./card.tsx"
import { Badge } from "./badge.tsx"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "./tooltip.tsx"
import { Input } from "./input.tsx"
import { Label } from "./label.tsx"
import { Textarea } from "./textarea.tsx"

type Event = {
  id: number
  title: string
  date: string
  time: string
  location: string
  description: string
  image: string
  organizer: {
    name: string
    logo: string
  }
}

const events: Event[] = [
  {
    id: 1,
    title: "Annual Tech Conference",
    date: "2024-03-15",
    time: "09:00 AM",
    location: "San Francisco Convention Center",
    description: "Join us for the biggest tech conference of the year, featuring keynotes from industry leaders and hands-on workshops on the latest technologies and trends shaping the future of the tech industry.",
    image: "https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8Mnx8dGVjaCUyMGNvbmZlcmVuY2V8ZW58MHx8MHx8fDA%3D",
    organizer: {
      name: "TechCorp",
      logo: "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?w=200&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8Mnx8bG9nb3xlbnwwfHwwfHx8MA%3D%3D"
    }
  },
  {
    id: 2,
    title: "Startup Pitch Night",
    date: "2024-04-22",
    time: "07:00 PM",
    location: "Innovation Hub, New York",
    description: "Watch promising startups pitch their groundbreaking ideas to a panel of venture capitalists and angel investors. Network with entrepreneurs and investors shaping the future of various industries.",
    image: "https://images.unsplash.com/photo-1551818255-e6e10975bc17?w=800&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8Nnx8c3RhcnR1cCUyMHBpdGNofGVufDB8fDB8fHww",
    organizer: {
      name: "StartupNY",
      logo: "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?w=200&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8Mnx8bG9nb3xlbnwwfHwwfHx8MA%3D%3D"
    }
  },
  {
    id: 3,
    title: "AI and Ethics Symposium",
    date: "2024-05-10",
    time: "10:00 AM",
    location: "Virtual Event",
    description: "Explore the ethical implications of AI in this day-long virtual symposium featuring global experts and thought leaders. Engage in discussions about the future of AI and its impact on society.",
    image: "https://images.unsplash.com/photo-1620712943543-bcc4688e7485?w=800&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8Mnx8YWklMjBldGhpY3N8ZW58MHx8MHx8fDA%3D",
    organizer: {
      name: "AI Ethics Institute",
      logo: "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?w=200&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8Mnx8bG9nb3xlbnwwfHwwfHx8MA%3D%3D"
    }
  },
  {
    id: 4,
    title: "Hackathon for Social Good",
    date: "2024-06-18",
    time: "09:00 AM",
    location: "Community Center, Chicago",
    description: "A 48-hour hackathon focused on developing tech solutions for pressing social issues. Open to developers of all skill levels, join us to make a positive impact through technology.",
    image: "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?w=800&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8Mnx8aGFja2F0aG9ufGVufDB8fDB8fHww",
    organizer: {
      name: "TechForGood",
      logo: "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?w=200&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8Mnx8bG9nb3xlbnwwfHwwfHx8MA%3D%3D"
    }
  }
]

const EventCard: React.FC<{ event: Event; onViewMore: () => void }> = ({ event, onViewMore }) => (
  <Card className="w-full bg-white/10 backdrop-blur-md border-none shadow-lg hover:shadow-xl transition-all duration-300 overflow-hidden group">
    <CardHeader >
      <Image src={event.image} alt={event.title} width={400} height={200} className="w-full h-48 object-cover transition-transform duration-300 group-hover:scale-110" />
      <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent transition-opacity duration-300 group-hover:opacity-100 flex items-end p-4">
        <CardTitle >{event.title}</CardTitle>
      </div>
    </CardHeader>
    <CardContent >
      <CardDescription >
        <MapPin className="w-4 h-4 mr-2" />
        <span>{event.location}</span>
      </CardDescription>
    </CardContent>
    <CardFooter >
      <Button variant="secondary" size="sm" className="flex-1 bg-white/20 hover:bg-white/30 transition-colors duration-300">
        <Calendar className="w-4 h-4 mr-2" />
        Going
      </Button>
      <Button variant="secondary" size="sm" className="flex-1 bg-white/20 hover:bg-white/30 transition-colors duration-300">
        <Ticket className="w-4 h-4 mr-2" />
        Tickets
      </Button>
      <Button variant="secondary" size="sm" onClick={onViewMore} className="flex-1 bg-white/20 hover:bg-white/30 transition-colors duration-300">
        <ChevronRight className="w-4 h-4 mr-2" />
        More
      </Button>
    </CardFooter>
  </Card>
)

const EventDetails: React.FC<{ event: Event; onClose: () => void }> = ({ event, onClose }) => (
  <Dialog open={true} onOpenChange={onClose}>
    <DialogContent className="sm:max-w-[425px] bg-gray-900/90 backdrop-blur-lg text-white">
      <DialogHeader>
        <DialogTitle>{event.title}</DialogTitle>
        <DialogDescription>
          Organized by {event.organizer.name}
        </DialogDescription>
      </DialogHeader>
      <div className="grid gap-4 py-4">
        <Image src={event.image} alt={event.title} width={400} height={200} className="w-full h-48 object-cover rounded-lg" />
        <div className="flex items-center text-gray-300">
          <Calendar className="w-4 h-4 mr-2" />
          <span>{event.date}</span>
        </div>
        <div className="flex items-center text-gray-300">
          <Clock className="w-4 h-4 mr-2" />
          <span>{event.time}</span>
        </div>
        <div className="flex items-center text-gray-300">
          <MapPin className="w-4 h-4 mr-2" />
          <span>{event.location}</span>
        </div>
        <p className="text-gray-100">{event.description}</p>
      </div>
      <div className="flex justify-between mt-4 gap-2">
        <Button variant="secondary" size="sm" className="flex-1 bg-white/20 hover:bg-white/30 transition-colors duration-300">
          <Calendar className="w-4 h-4 mr-2" />
          Going
        </Button>
        <Button variant="secondary" size="sm" className="flex-1 bg-white/20 hover:bg-white/30 transition-colors duration-300">
          <Ticket className="w-4 h-4 mr-2" />
          Tickets
        </Button>
      </div>
      <DialogClose asChild>
        <Button className="absolute right-4 top-4" variant="ghost" size="icon">
          <X className="h-4 w-4" />
          <span className="sr-only">Close</span>
        </Button>
      </DialogClose>
    </DialogContent>
  </Dialog>
)
const TimelineItem: React.FC<{ event: Event; index: number; onViewMore: () => void }> = ({ event, index, onViewMore }) => {
  const controls = useAnimation();
  const ref = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const element = ref.current; // Store the initial ref.current in a variable

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          controls.start("visible");
        }
      },
      { threshold: 0.1 }
    );

    if (element) {
      observer.observe(element);
    }

    return () => {
      if (element) { // Use the stored element in the cleanup function
        observer.unobserve(element);
      }
    };
  }, [controls]);


  const itemVariants = {
    hidden: { opacity: 0, y: 50 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.5,
        ease: "easeOut",
        when: "beforeChildren",
        staggerChildren: 0.2,
      },
    },
  }

  const childVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.5 },
    },
  }

  return (
    <motion.div
      ref={ref}
      initial="hidden"
      animate={controls}
      variants={itemVariants}
      className={`mb-16 flex flex-col ${index % 2 === 0 ? 'md:flex-row' : 'md:flex-row-reverse'}`}
    >
      <motion.div variants={childVariants} className="w-full md:w-5/12 mb-4 md:mb-0">
        <EventCard event={event} onViewMore={onViewMore} />
      </motion.div>
      <motion.div variants={childVariants} className="w-full md:w-2/12 flex justify-center items-center my-4 md:my-0">
        <div className="w-16 h-16 bg-white/20 backdrop-blur-sm rounded-full flex items-center justify-center p-1 border-4 border-white/30">
          <Image src={event.organizer.logo} alt={event.organizer.name} width={50} height={50} className="rounded-full" />
        </div>
      </motion.div>
      <motion.div variants={childVariants} className={`w-full md:w-5/12 flex flex-col ${index % 2 === 0 ? 'md:items-start' : 'md:items-end'}`}>
        <div className="mb-2 text-center md:text-left">
          <Badge variant="secondary">
            <Calendar className="w-4 h-4 mr-2 inline-block" />
            {event.date}
          </Badge>
          <br />
          <Badge variant="outline">
            <Clock className="w-4 h-4 mr-2 inline-block" />
            {event.time}
          </Badge>
        </div>
        <div className="flex flex-wrap gap-2 justify-center md:justify-start">
          <TooltipProvider>
            <Tooltip title="Add this event to your calendar">
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="sm"
                  className="bg-white/10 text-white border-gray-600 hover:bg-white/20 transition-colors duration-300"
                >
                  <Plus className="w-4 h-4 mr-2" />
                  <span className="hidden sm:inline">Add to Calendar</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Add to Calendar</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="outline" size="sm" className="bg-white/10 text-white border-gray-600 hover:bg-white/20 transition-colors duration-300">
                  <Navigation className="w-4 h-4 mr-2" />
                  <span className="hidden sm:inline">Get Directions</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Get Directions</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="outline" size="sm" className="bg-white/10 text-white border-gray-600 hover:bg-white/20 transition-colors duration-300">
                  <Share2 className="w-4 h-4 mr-2" />
                  <span className="hidden sm:inline">Share</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Share Event</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      </motion.div>
    </motion.div>
  )
}

const CreateEventModal: React.FC<{ isOpen: boolean; onClose: () => void; onCreateEvent: (event: Omit<Event, 'id'>) => void }> = ({ isOpen, onClose, onCreateEvent }) => {
  const [newEvent, setNewEvent] = useState<Omit<Event, 'id'>>({
    title: '',
    date: '',
    time: '',
    location: '',
    description: '',
    image: '',
    organizer: {
      name: '',
      logo: ''
    }
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onCreateEvent(newEvent)
    onClose()
  }

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[425px] bg-gray-900/90 backdrop-blur-lg text-white">
        <DialogHeader>
          <DialogTitle>Create New Event</DialogTitle>
          <DialogDescription>
            Fill in the details for your new event.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="title"  >
                Title
              </Label>
              <Input
                id="title"
                value={newEvent.title}
                onChange={(e) => setNewEvent({ ...newEvent, title: e.target.value })}
                className="col-span-3 bg-white/10 border-gray-600 text-white"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="date"  >
                Date
              </Label>
              <Input
                id="date"
                type="date"
                value={newEvent.date}
                onChange={(e) => setNewEvent({ ...newEvent, date: e.target.value })}
                className="col-span-3 bg-white/10 border-gray-600 text-white"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="time"  >
                Time
              </Label>
              <Input
                id="time"
                type="time"
                value={newEvent.time}
                onChange={(e) => setNewEvent({ ...newEvent, time: e.target.value })}
                className="col-span-3 bg-white/10 border-gray-600 text-white"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="location"  >
                Location
              </Label>
              <Input
                id="location"
                value={newEvent.location}
                onChange={(e) => setNewEvent({ ...newEvent, location: e.target.value })}
                className="col-span-3 bg-white/10 border-gray-600 text-white"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="description">
                Description
              </Label>
              <Textarea
                id="description"
                value={newEvent.description}
                onChange={(e) => setNewEvent({ ...newEvent, description: e.target.value })}
                className="col-span-3 bg-white/10 border-gray-600 text-white"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="image">
                Image URL
              </Label>
              <Input
                id="image"
                value={newEvent.image}
                onChange={(e) => setNewEvent({ ...newEvent, image: e.target.value })}
                className="col-span-3 bg-white/10 border-gray-600 text-white"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="organizer"  >
                Organizer
              </Label>
              <Input
                id="organizer"
                value={newEvent.organizer.name}
                onChange={(e) => setNewEvent({ ...newEvent, organizer: { ...newEvent.organizer, name: e.target.value } })}
                className="col-span-3 bg-white/10 border-gray-600 text-white"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="logo">
                Logo URL
              </Label>
              <Input
                id="logo"
                value={newEvent.organizer.logo}
                onChange={(e) => setNewEvent({ ...newEvent, organizer: { ...newEvent.organizer, logo: e.target.value } })}
                className="col-span-3 bg-white/10 border-gray-600 text-white"
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="submit" className="bg-white/10 text-white hover:bg-white/20 transition-colors duration-300">Create Event</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function ScrollRevealEventTimeline() {
  const [selectedEvent, setSelectedEvent] = useState<Event | null>(null)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [eventList, setEventList] = useState<Event[]>(events)

  const handleCreateEvent = (newEvent: Omit<Event, 'id'>) => {
    const eventWithId = { ...newEvent, id: eventList.length + 1 }
    setEventList([...eventList, eventWithId])
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-900 via-purple-900 to-violet-800 text-white py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-6xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="flex justify-between items-center mb-16"
        >
          <h1 className="text-4xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-purple-400 to-pink-600">
            Upcoming Events
          </h1>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <motion.button
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  className="rounded-full bg-white/10 border-2 border-white/30 text-white hover:bg-white/20 transition-colors duration-300 p-2"
                  onClick={() => setIsCreateModalOpen(true)}
                >
                  <Plus className="h-6 w-6" />
                  <span className="sr-only">Create Event</span>
                </motion.button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Create New Event</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </motion.div>
        <div className="relative">
          {/* Vertical line */}
          <div className="absolute left-1/2 transform -translate-x-1/2 w-1 h-full bg-gradient-to-b from-purple-500 to-pink-500 rounded-full hidden md:block" aria-hidden="true"></div>
          
          {/* Event items */}
          {eventList.map((event, index) => (
            <TimelineItem 
              key={event.id}
              event={event} 
              index={index} 
              onViewMore={() => setSelectedEvent(event)} 
            />
          ))}
        </div>
      </div>

      {/* Event Details Dialog */}
      <AnimatePresence>
        {selectedEvent && (
          <EventDetails event={selectedEvent} onClose={() => setSelectedEvent(null)} />
        )}
      </AnimatePresence>

      {/* Create Event Modal */}
      <CreateEventModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onCreateEvent={handleCreateEvent}
      />
    </div>
  )
}