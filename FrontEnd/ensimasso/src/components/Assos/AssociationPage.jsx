import React from 'react';
import { useState, useEffect, useRef } from 'react';
import { useUser } from '../../contexts/UserContext';
import 'react-responsive-carousel/lib/styles/carousel.min.css';

import { motion } from 'framer-motion';
import { useForm } from 'react-hook-form';
import ShootingStars from '../Background/ShootingStars';
import styled from 'styled-components';
import 'flowbite';

import { Swiper, SwiperSlide } from 'swiper/react';
import 'swiper/css'; // Swiper core styles
import 'swiper/css/navigation'; // Navigation module styles
import 'swiper/css/pagination'; // Pagination module styles
import { Navigation, Pagination } from 'swiper/modules';
import EarthCanvas from '../Assos/canvas/Earth';

// Animation variants for Framer Motion
const fadeIn = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.6 } },
};

// Custom Styles
const styles = {
  sectionTitle: "text-4xl font-extrabold mb-8 text-center text-white font-poppins",
  card: " p-6 rounded-xl shadow-xl hover:shadow-2xl transition-all transform hover:-translate-y-2",
  buttonPrimary: "bg-gradient-to-r from-blue-600 to-blue-500 text-white px-6 py-3 rounded-lg hover:from-blue-700 hover:to-blue-600 transition-all",
  inputField: "w-full p-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent",
};


const AboutSection = () => {
  const content = {
    title: "Asso Name",
    description:
      "Asso Description non deserunt ullamco est sit aliqua dolor do amet sint. Velit officia consequat duis enim velit mollit. Exercitation veniam consequat.",
    actions: [
      {
        label: "Addhésion",
        href: "#",
        className:
          "inline-flex items-center justify-center w-full px-8 py-3 text-lg font-bold text-white transition-all duration-200 bg-gray-900 border-2 border-transparent sm:w-auto rounded-xl font-pj hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-900",
      },
      {
        label: "Join Us",
        href: "#",
        className:
          "inline-flex items-center justify-center w-full px-6 py-3 mt-4 text-lg font-bold text-white transition-all duration-200 border-2 border-gray-400 sm:w-auto sm:mt-0 rounded-xl font-pj focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-900 hover:bg-gray-900 focus:bg-gray-900 hover:text-white focus:text-white hover:border-gray-900 focus:border-gray-900",
        icon: (
          <svg
            className="w-5 h-5 mr-2"
            viewBox="0 0 18 18"
            fill="none"
            stroke="currentColor"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M8.18003 13.4261C6.8586 14.3918 5 13.448 5 11.8113V5.43865C5 3.80198 6.8586 2.85821 8.18003 3.82387L12.5403 7.01022C13.6336 7.80916 13.6336 9.44084 12.5403 10.2398L8.18003 13.4261Z"
              strokeWidth="2"
              strokeMiterlimit="10"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        ),
      },
    ],
    socialMedia: [
      {
        name: 'Facebook',
        href: '#',
        icon: (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path fillRule="evenodd" d="M22 12c0-5.523-4.477-10-10-10S2 6.477 2 12c0 4.991 3.657 9.128 8.438 9.878v-6.987h-2.54V12h2.54V9.797c0-2.506 1.492-3.89 3.777-3.89 1.094 0 2.238.195 2.238.195v2.46h-1.26c-1.243 0-1.63.771-1.63 1.562V12h2.773l-.443 2.89h-2.33v6.988C18.343 21.128 22 16.991 22 12z" clipRule="evenodd" />
          </svg>
        )
      },
      {
        name: 'Twitter',
        href: '#',
        icon: (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path d="M8.29 20.251c7.547 0 11.675-6.253 11.675-11.675 0-.178 0-.355-.012-.53A8.348 8.348 0 0022 5.92a8.19 8.19 0 01-2.357.646 4.118 4.118 0 001.804-2.27 8.224 8.224 0 01-2.605.996 4.107 4.107 0 00-6.993 3.743 11.65 11.65 0 01-8.457-4.287 4.106 4.106 0 001.27 5.477A4.072 4.072 0 012.8 9.713v.052a4.105 4.105 0 003.292 4.022 4.095 4.095 0 01-1.853.07 4.108 4.108 0 003.834 2.85A8.233 8.233 0 012 18.407a11.616 11.616 0 006.29 1.84" />
          </svg>
        )
      },
      {
        name: 'Instagram',
        href: '#',
        icon: (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path fillRule="evenodd" d="M12.315 2c2.43 0 2.784.013 3.808.06 1.064.049 1.791.218 2.427.465a4.902 4.902 0 011.772 1.153 4.902 4.902 0 011.153 1.772c.247.636.416 1.363.465 2.427.048 1.067.06 1.407.06 4.123v.08c0 2.643-.012 2.987-.06 4.043-.049 1.064-.218 1.791-.465 2.427a4.902 4.902 0 01-1.153 1.772 4.902 4.902 0 01-1.772 1.153c-.636.247-1.363.416-2.427.465-1.067.048-1.407.06-4.123.06h-.08c-2.643 0-2.987-.012-4.043-.06-1.064-.049-1.791-.218-2.427-.465a4.902 4.902 0 01-1.772-1.153 4.902 4.902 0 01-1.153-1.772c-.247-.636-.416-1.363-.465-2.427-.047-1.024-.06-1.379-.06-3.808v-.63c0-2.43.013-2.784.06-3.808.049-1.064.218-1.791.465-2.427a4.902 4.902 0 011.153-1.772A4.902 4.902 0 015.45 2.525c.636-.247 1.363-.416 2.427-.465C8.901 2.013 9.256 2 11.685 2h.63zm-.081 1.802h-.468c-2.456 0-2.784.011-3.807.058-.975.045-1.504.207-1.857.344-.467.182-.8.398-1.15.748-.35.35-.566.683-.748 1.15-.137.353-.3.882-.344 1.857-.047 1.023-.058 1.351-.058 3.807v.468c0 2.456.011 2.784.058 3.807.045.975.207 1.504.344 1.857.182.466.399.8.748 1.15.35.35.683.566 1.15.748.353.137.882.3 1.857.344 1.054.048 1.37.058 4.041.058h.08c2.597 0 2.917-.01 3.96-.058.976-.045 1.505-.207 1.858-.344.466-.182.8-.398 1.15-.748.35-.35.566-.683.748-1.15.137-.353.3-.882.344-1.857.048-1.055.058-1.37.058-4.041v-.08c0-2.597-.01-2.917-.058-3.96-.045-.976-.207-1.505-.344-1.858a3.097 3.097 0 00-.748-1.15 3.098 3.098 0 00-1.15-.748c-.353-.137-.882-.3-1.857-.344-1.023-.047-1.351-.058-3.807-.058zM12 6.865a5.135 5.135 0 110 10.27 5.135 5.135 0 010-10.27zm0 1.802a3.333 3.333 0 100 6.666 3.333 3.333 0 000-6.666zm5.338-3.205a1.2 1.2 0 110 2.4 1.2 1.2 0 010-2.4z" clipRule="evenodd" />
          </svg>
        )
      },
    ],
    image: "https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80",
  };

  return (
    <motion.section
      initial="hidden"
      animate="visible"
      variants={fadeIn}
      className="relative py-12 overflow-hidden bg-transparent sm:pb-16 lg:pb-20 xl:pb-24"

    >
      <div className="px-4 mx-auto max-w-7xl sm:px-6 lg:px-8">
        <div className="grid items-center grid-cols-1 gap-y-12 lg:grid-cols-2 gap-x-16">
          {/* Text Content */}
          <div className="mt-6 mb-12 gap-y-16">
            <h1 className="text-4xl font-bold text-white sm:text-5xl lg:text-6xl xl:text-7xl">
              {content.title}
            </h1>
            <p className="mt-4 text-lg font-normal text-gray-400 sm:mt-8">
              {content.description}
            </p>
            {/* Action Buttons */}
            <div className="px-8 sm:items-center sm:justify-center sm:px-0 sm:space-x-5 sm:flex mt-9">
              {content.actions.map((action, index) => (
                <a
                  key={index}
                  href={action.href}
                  className={action.className}
                  role="button"
                >
                  {action.icon}
                  {action.label}
                </a>
              ))}
            </div>

            {/* Social Media Section */}
            <div className="mt-8 sm:mt-6">
              <p className="text-lg font-normal text-white">Follow us on social media</p>
              <div className="flex items-center mt-4 space-x-4">
                {content.socialMedia.map((social, index) => (
                  <a
                    key={index}
                    href={social.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-gray-400 hover:text-white transition-colors duration-200"
                  >
                    <span className="sr-only">{social.name}</span>
                    {social.icon}
                  </a>
                ))}
              </div>
            </div>
          </div>
          
          {/* Image Section */}
          <div className="mb-12 hidden lg:block">
            <img
              src={content.image}
              alt="Section Visual"
              className="rounded-xl shadow-xl"
            />
          </div>
        </div>
      </div>
    </motion.section>
  );
};



// Team Section
const TeamSection = () => {
  const team = [
    {
        avatar: "https://images.unsplash.com/photo-1511485977113-f34c92461ad9?ixlib=rb-1.2.1&q=80&fm=jpg&crop=faces&fit=crop&h=200&w=200&ixid=eyJhcHBfaWQiOjE3Nzg0fQ",
        name: "Martiana dialan",
        title: "Product designer",
        linkedin: "javascript:void(0)",
        instagram: "javascript:void(0)",
    },
    {
        avatar: "https://api.uifaces.co/our-content/donated/xZ4wg2Xj.jpg",
        name: "Micheal colorand",
        title: "Software engineer",
        linkedin: "javascript:void(0)",
        instagram: "javascript:void(0)",
    },
    {
        avatar: "https://randomuser.me/api/portraits/women/79.jpg",
        name: "Brown Luis",
        title: "Full stack engineer",
        linkedin: "javascript:void(0)",
        instagram: "javascript:void(0)",
    },
    {
        avatar: "https://randomuser.me/api/portraits/women/63.jpg",
        name: "Lysa sandiago",
        title: "Head of designers",
        linkedin: "javascript:void(0)",
        instagram: "javascript:void(0)",
    },
    {
        avatar: "https://randomuser.me/api/portraits/men/86.jpg",
        name: "Daniel martin",
        title: "Product designer",
        linkedin: "javascript:void(0)",
        instagram: "javascript:void(0)",
    },
    {
        avatar: "https://randomuser.me/api/portraits/men/46.jpg",
        name: "Vicky tanson",
        title: "Product manager",
        linkedin: "javascript:void(0)",
        instagram: "javascript:void(0)",
    },
  ]
  return (
    <motion.section initial="hidden" animate="visible" variants={fadeIn} className="py-12 " style={styles}>
      <div>
          <div className="max-w-screen-xl mx-auto px-4 md:px-8">
              <div className="max-w-xl">
                  <h3 className="text-white text-3xl font-semibold sm:text-4xl">
                      Meet our team
                  </h3>
                  <p className="text-white mt-3">
                      Lorem Ipsum is simply dummy text of the printing and typesetting industry.Lorem Ipsum has been the industry's standard dummy.
                  </p>
              </div>
              <div className="mt-6">
                  <ul className="grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
                      {
                          team.map((item, idx) => (
                              <li key={idx} className="flex gap-4 items-center">
                                  <div className="flex-none w-24 h-24">
                                      <img
                                          src={item.avatar}
                                          className="w-full h-full rounded-full"
                                          alt=""
                                      />
                                  </div>
                                  <div>
                                      <h4 className="text-white font-semibold sm:text-lg">{item.name}</h4>
                                      <p className="text-white">{item.title}</p>
                                      <div className="mt-3 flex gap-4 text-white">
                                        <a href={item.instagram}>
                                            <svg className="w-5 h-5 duration-150 hover:text-gray-500" fill="currentColor" viewBox="0 0 24 24">
                                                <path d="M12 2.2c3.2 0 3.584.012 4.85.07 1.173.055 1.98.247 2.68.527a5.38 5.38 0 011.918 1.254 5.38 5.38 0 011.254 1.918c.28.7.472 1.507.527 2.68.058 1.266.07 1.65.07 4.85s-.012 3.584-.07 4.85c-.055 1.173-.247 1.98-.527 2.68a5.38 5.38 0 01-1.254 1.918 5.38 5.38 0 01-1.918 1.254c-.7.28-1.507.472-2.68.527-1.266.058-1.65.07-4.85.07s-3.584-.012-4.85-.07c-1.173-.055-1.98-.247-2.68-.527a5.38 5.38 0 01-1.918-1.254 5.38 5.38 0 01-1.254-1.918c-.28-.7-.472-1.507-.527-2.68C2.212 15.584 2.2 15.2 2.2 12s.012-3.584.07-4.85c.055-1.173.247-1.98.527-2.68a5.38 5.38 0 011.254-1.918 5.38 5.38 0 011.918-1.254c.7-.28 1.507-.472 2.68-.527C8.416 2.212 8.8 2.2 12 2.2zm0 1.8c-3.142 0-3.522.012-4.77.07-.979.045-1.51.21-1.865.35-.47.181-.805.4-1.157.752-.352.352-.57.687-.752 1.157-.14.355-.305.886-.35 1.865-.058 1.248-.07 1.628-.07 4.77s.012 3.522.07 4.77c.045.979.21 1.51.35 1.865.181.47.4.805.752 1.157.352.352.687.57 1.157.752.355.14.886.305 1.865.35 1.248.058 1.628.07 4.77.07s3.522-.012 4.77-.07c.979-.045 1.51-.21 1.865-.35.47-.181.805-.4 1.157-.752.352-.352.57-.687.752-1.157.14-.355.305-.886.35-1.865.058-1.248.07-1.628.07-4.77s-.012-3.522-.07-4.77c-.045-.979-.21-1.51-.35-1.865-.181-.47-.4-.805-.752-1.157-.352-.352-.687-.57-1.157-.752-.355-.14-.886-.305-1.865-.35-1.248-.058-1.628-.07-4.77-.07zm0 3.3a6.5 6.5 0 110 13 6.5 6.5 0 010-13zm0 1.8a4.7 4.7 0 100 9.4 4.7 4.7 0 000-9.4zm6.5-2.9a1.5 1.5 0 110 3 1.5 1.5 0 010-3z"/>
                                            </svg>
                                        </a>
                                        <a href={item.linkedin}>
                                            <svg className="w-5 h-5 duration-150 hover:text-gray-500" fill="none" viewBox="0 0 48 48"><g clip-path="url(#clip0_17_68)"><path fill="currentColor" d="M44.447 0H3.544C1.584 0 0 1.547 0 3.46V44.53C0 46.444 1.584 48 3.544 48h40.903C46.407 48 48 46.444 48 44.54V3.46C48 1.546 46.406 0 44.447 0zM14.24 40.903H7.116V17.991h7.125v22.912zM10.678 14.87a4.127 4.127 0 01-4.134-4.125 4.127 4.127 0 014.134-4.125 4.125 4.125 0 010 8.25zm30.225 26.034h-7.115V29.766c0-2.653-.047-6.075-3.704-6.075-3.703 0-4.265 2.896-4.265 5.887v11.325h-7.107V17.991h6.826v3.13h.093c.947-1.8 3.272-3.702 6.731-3.702 7.21 0 8.541 4.744 8.541 10.912v12.572z" /></g><defs><clipPath id="clip0_17_68"><path fill="currentColor" d="M0 0h48v48H0z" /></clipPath></defs></svg>
                                        </a>
                                      </div>
                                  </div>
                              </li>
                          ))
                      }
                  </ul>
              </div>
          </div>
      </div>
    </motion.section>
  );
};

// Events Section
const EventsSection = () => {
  const events = [
    {
      title: 'Past Event 1',
      image: 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2022-01-15',
      type: 'past',
    },
    {
      title: 'Upcoming Event 2',
      image: 'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2023-05-20',
      type: 'upcoming',
    },
    {
      title: 'Past Event 3',
      image: 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2021-12-10',
      type: 'past',
    },
    {
      title: 'Upcoming Event 4',
      image: 'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2023-06-25',
      type: 'upcoming',
    },
    {
      title: 'Past Event 1',
      image: 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2022-01-15',
      type: 'past',
    },
    {
      title: 'Upcoming Event 2',
      image: 'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2023-05-20',
      type: 'upcoming',
    },
    {
      title: 'Past Event 3',
      image: 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2021-12-10',
      type: 'past',
    },
    {
      title: 'Upcoming Event 4',
      image: 'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2023-06-25',
      type: 'upcoming',
    },
    {
      title: 'Past Event 1',
      image: 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2022-01-15',
      type: 'past',
    },
    {
      title: 'Upcoming Event 2',
      image: 'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2023-05-20',
      type: 'upcoming',
    },
    {
      title: 'Past Event 3',
      image: 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2021-12-10',
      type: 'past',
    },
    {
      title: 'Upcoming Event 4',
      image: 'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60',
      date: '2023-06-25',
      type: 'upcoming',
    },
  ];

  const [showUpcoming, setShowUpcoming] = useState(true);

  // Filter events based on the switch state
  const filteredEvents = events.filter((event) =>
    showUpcoming ? event.type === 'past' : event.type === 'upcoming'
  );

  return (
    <motion.section
      initial="hidden"
      animate="visible"
      variants={{
        hidden: { opacity: 0 },
        visible: { opacity: 1, transition: { duration: 0.5 } },
      }}

    >
      <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '0 24px' }}>
        <h2 style={{ fontSize: '2.5rem', color: 'white', textAlign: 'center', marginBottom: '40px' }}>
          Events
        </h2>
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '40px' }}>
          <StyledWrapper>
            <label htmlFor="filter" className="switch" aria-label="Toggle Filter">
              <input
                type="checkbox"
                id="filter"
                checked={showUpcoming}
                onChange={() => setShowUpcoming(!showUpcoming)}
              />
              <span>Upcoming</span>
              <span>Past</span>
            </label>
          </StyledWrapper>
        </div>

        {/* Swiper Carousel */}
        <Swiper
          modules={[Navigation, Pagination]}
          spaceBetween={30}
          slidesPerView={1}
          navigation
          pagination={{ clickable: true }}
          breakpoints={{
            640: {
              slidesPerView: 2,
            },
            1024: {
              slidesPerView: 3,
            },
          }}
        >
          {filteredEvents.map((event, index) => (
            <SwiperSlide key={index}>
              <div
                className="animated-card"
                style={{
                  position: 'relative',
                  borderRadius: '16px',
                  overflow: 'hidden',
                  padding: '20px',
                  backgroundColor: 'rgba(255, 255, 255, 0.05)',
                  backdropFilter: 'blur(10px)',
                  boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
                }}
              >
                <img
                  src={event.image}
                  alt={event.title}
                  style={{ width: '100%', height: '192px', objectFit: 'cover', borderRadius: '12px', marginBottom: '16px' }}
                />
                <h3 style={{ color: 'white', fontSize: '1.5rem', fontWeight: 'bold', marginBottom: '8px' }}>
                  {event.title}
                </h3>
                <p style={{ color: 'white', fontSize: '1rem' }}>Date: {event.date}</p>
              </div>
            </SwiperSlide>
          ))}
        </Swiper>
      </div>

      {/* Custom Styles for Swiper Arrows and Pagination */}
      <style>
        {`
          /* White Arrows */
          .swiper-button-next,
          .swiper-button-prev {
            color: white !important; /* Change arrow color to white */
          }

          /* White Pagination Bullets */
          .swiper-pagination-bullet {
            background: white !important; /* Change bullet color to white */
            opacity: 0.5 !important; /* Make bullets semi-transparent */
          }

          .swiper-pagination-bullet-active {
            opacity: 1 !important; /* Make active bullet fully opaque */
          }
        `}
      </style>

      {/* Animation Styles */}
      <style>
        {`
          .animated-card {
            position: relative;
            border: 2px solid transparent;
            transform-style: preserve-3d;
            transition: transform 0.5s ease, box-shadow 0.3s ease;
            perspective: 1000px;
          }

          .animated-card:hover {
            transform: scale(1.05) rotateX(8deg) rotateY(8deg);
            box-shadow: 0 12px 48px rgba(0, 0, 0, 0.4);
          }

          .animated-card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            border-radius: 16px;
            border: 2px solid rgba(255, 255, 255, 0.2);
            box-shadow: 0 0 15px rgba(255, 255, 255, 0.3);
            z-index: 1;
            pointer-events: none;
          }

          .animated-card:hover::before {
            box-shadow: 0 0 20px rgba(255, 255, 255, 0.5);
          }
        `}
      </style>
    </motion.section>
  );
};

// Styled Components for the Switch
const StyledWrapper = styled.div`
  .switch {
    --_switch-bg-clr: rgb(22, 22, 22);
    --_switch-padding: 4px;
    --_slider-bg-clr: rgb(255, 255, 255);
    --_slider-bg-clr-on: rgb(255, 255, 255);
    --_slider-txt-clr: white;
    --_slider-txt-clr-on: black;
    --_label-padding: 1rem 2rem;
    --_switch-easing: cubic-bezier(0.47, 1.64, 0.41, 0.8);
    color: var(--_slider-txt-clr);
    width: fit-content;
    display: flex;
    justify-content: center;
    position: relative;
    border-radius: 9999px;
    cursor: pointer;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    isolation: isolate;
  }

  .switch input[type="checkbox"] {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border-width: 0;
  }
  .switch > span {
    display: grid;
    place-content: center;
    transition: color 300ms ease-in-out, opacity 300ms ease-in-out 150ms;
    padding: var(--_label-padding);
  }
  .switch:has(input:checked) > span:first-of-type {
    color: var(--_slider-txt-clr);
  }
  .switch:has(input:not(:checked)) > span:last-of-type {
    color: var(--_slider-txt-clr);
  }
  .switch:has(input:checked) > span:last-of-type {
    color: var(--_slider-txt-clr-on);
  }
  .switch:has(input:not(:checked)) > span:first-of-type {
    color: var(--_slider-txt-clr-on);
  }

  .switch::before,
  .switch::after {
    content: "";
    position: absolute;
    border-radius: inherit;
    transition: inset 150ms ease-in-out;
  }

  .switch::before {
    background-color: var(--_slider-bg-clr);
    inset: var(--_switch-padding) 50% var(--_switch-padding)
      var(--_switch-padding);
    transition: inset 500ms var(--_switch-easing), background-color 500ms ease-in-out;
    z-index: -1;
    box-shadow: inset 0 1px 1px rgba(0, 0, 0, 0.3), 0 1px rgba(255, 255, 255, 0.3);
  }

  .switch::after {
    background-color: var(--_switch-bg-clr);
    inset: 0;
    z-index: -2;
  }

  .switch:focus-within::after {
    inset: -0.25rem;
  }

  .switch:has(input:checked)::before {
    background-color: var(--_slider-bg-clr-on);
    inset: var(--_switch-padding) var(--_switch-padding) var(--_switch-padding)
      50%;
  }
`;



// Gallery Component
const GallerySection = () => {
  const images = [
    'https://images.unsplash.com/photo-1506744038136-46273834b3fb?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80', // High-res Landscape 1
    'https://images.unsplash.com/photo-1510784722466-f2aa9c52fff6?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80', // High-res Landscape 2
    'https://images.unsplash.com/photo-1501785888041-af3ef285b470?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80', // High-res Landscape 3
    'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80', // High-res Landscape 4
    'https://images.unsplash.com/photo-1494500764479-0c8f2919a3d8?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80', // High-res Landscape 5
    'https://images.unsplash.com/photo-1475924156734-496f6cac6ec1?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80', // High-res Landscape 6
    'https://images.unsplash.com/photo-1505144808419-1957a94ca61e?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80', // High-res Landscape 7
    'https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?ixlib=rb-1.2.1&auto=format&fit=crop&w=1200&q=80', // High-res Landscape 8
  ];

  const [selectedImage, setSelectedImage] = useState(null);

  const handleImageClick = (image) => {
    setSelectedImage(image);
  };

  const closePopup = () => {
    setSelectedImage(null);
  };

  return (
    <GalleryContainer style={styles} >
      <h2 style={{ fontSize: '2.5rem', color: 'white', textAlign: 'center', marginBottom: '40px' }}>
        Gallery
      </h2>
      {/* First Line: Moves to the Right */}
      <MarqueeLine direction="right">
        {images.map((image, index) => (
          <img
            key={index}
            src={image}
            alt={`Gallery Image ${index}`}
            onClick={() => handleImageClick(image)}
          />
        ))}
        {/* Duplicate images for seamless looping */}
        {images.map((image, index) => (
          <img
            key={`dup-${index}`}
            src={image}
            alt={`Gallery Image ${index}`}
            onClick={() => handleImageClick(image)}
          />
        ))}
      </MarqueeLine>

      {/* Gap Between Lines */}
      <div style={{ marginBottom: '40px' }}></div>

      {/* Second Line: Moves to the Left */}
      <MarqueeLine direction="left">
        {images.map((image, index) => (
          <img
            key={index}
            src={image}
            alt={`Gallery Image ${index}`}
            onClick={() => handleImageClick(image)}
          />
        ))}
        {/* Duplicate images for seamless looping */}
        {images.map((image, index) => (
          <img
            key={`dup-${index}`}
            src={image}
            alt={`Gallery Image ${index}`}
            onClick={() => handleImageClick(image)}
          />
        ))}
      </MarqueeLine>

      {/* Popup for Selected Image */}
      {selectedImage && (
        <PopupOverlay onClick={closePopup}>
          <PopupContent onClick={(e) => e.stopPropagation()}>
            <img
              src={selectedImage}
              alt="Selected"
              style={{
                width: '50vw',
                height: '50vh',
                objectFit: 'cover',
                borderRadius: '16px',
              }}
            />
          </PopupContent>
        </PopupOverlay>
      )}
    </GalleryContainer>
  );
};

// Styled Components
const GalleryContainer = styled.div`
  width: 100%;
  overflow: hidden;
  position: relative;
  padding: 20px 0;
  background-color: transparent;
`;

const MarqueeLine = styled.div`
  display: flex;
  width: max-content;
  animation: ${({ direction }) => (direction === 'right' ? 'marqueeRight' : 'marqueeLeft')} 20s linear infinite;

  img {
    width: 200px;
    height: 150px;
    margin: 0 10px;
    border-radius: 8px;
    object-fit: cover;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
    transition: transform 0.3s ease;
    cursor: pointer;

    &:hover {
      transform: scale(1.1); /* Add a hover effect to images */
    }
  }

  @keyframes marqueeRight {
    0% {
      transform: translateX(0);
    }
    100% {
      transform: translateX(-50%);
    }
  }

  @keyframes marqueeLeft {
    0% {
      transform: translateX(-50%);
    }
    100% {
      transform: translateX(0);
    }
  }
`;

const PopupOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.8);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const PopupContent = styled.div`
  background-color: transparent;
  padding: 20px;
  border-radius: 16px; /* Add border radius to the container */
  text-align: center;
  max-width: 90vw;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  justify-content: center;
  align-items: center;
`;


const CreditCard3D = () => {
  // Inline styles
  const styles = {
    container: {
      perspective: '1000px',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
    },
    cardWrapper: {
      position: 'relative',
      transformStyle: 'preserve-3d',
      animation: 'float 8s ease-in-out infinite',
    },
    card: {
      width: '320px',
      height: '200px',
      position: 'relative',
      transformStyle: 'preserve-3d',
      borderRadius: '15px',
      animation: 'infinityRotation 12s linear infinite',
      boxShadow: '0 20px 40px rgba(0, 0, 0, 0.2)',
    },
    cardFace: {
      position: 'absolute',
      width: '100%',
      height: '100%',
      backfaceVisibility: 'hidden',
      borderRadius: '15px',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'space-between',
      padding: '25px',
      boxSizing: 'border-box',
      overflow: 'hidden',
    },
    cardFront: {
      background: 'linear-gradient(135deg, #1a1a1a, #4a4a4a)',
      color: 'white',
      transform: 'translateZ(1px)',
    },
    cardBack: {
      background: 'linear-gradient(135deg, #2d2d2d, #5a5a5a)',
      transform: 'rotateY(180deg) translateZ(1px)',
    },
    bankLogo: {
      alignSelf: 'flex-end',
      width: '60px',
      filter: 'brightness(0) invert(1)',
    },
    chip: {
      width: '45px',
      height: '35px',
      background: 'linear-gradient(45deg, #d4af37, #c0c0c0)',
      borderRadius: '5px',
      position: 'relative',
      boxShadow: 'inset 0 0 10px rgba(0,0,0,0.2)',
    },
    cardNumber: {
      fontSize: '1.2em',
      letterSpacing: '3px',
      fontFamily: 'monospace',
      textShadow: '0 2px 4px rgba(0,0,0,0.3)',
    },
    cardHolder: {
      fontSize: '0.9em',
      textTransform: 'uppercase',
      letterSpacing: '1px',
    },
    expiryDate: {
      fontSize: '0.9em',
      letterSpacing: '1px',
    },
    hologram: {
      position: 'absolute',
      top: '30px',
      right: '30px',
      width: '50px',
      height: '50px',
      background: 'linear-gradient(45deg, rgba(255,255,255,0.3), rgba(255,255,255,0.1))',
      borderRadius: '50%',
      transform: 'rotate(45deg)',
    },
    magneticStrip: {
      width: '100%',
      height: '40px',
      background: 'linear-gradient(90deg, #1a1a1a, #333333)',
      marginTop: '20px',
    },
    signatureStrip: {
      width: '80%',
      height: '30px',
      background: '#ffffff',
      marginTop: '15px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'flex-end',
      paddingRight: '10px',
      fontSize: '0.8em',
      color: '#333333',
    },
    cvv: {
      background: '#ffffff',
      color: '#333333',
      padding: '3px 10px',
      borderRadius: '3px',
      fontFamily: 'monospace',
    },
    glossyOverlay: {
      position: 'absolute',
      top: '0',
      left: '0',
      width: '100%',
      height: '100%',
      background: 'linear-gradient(135deg, rgba(255,255,255,0.1), rgba(255,255,255,0))',
      pointerEvents: 'none',
    },
    contactlessSymbol: {
      position: 'absolute',
      top: '25px',
      right: '100px',
      width: '30px',
      height: '30px',
      background: 'radial-gradient(circle, #ffffff 40%, transparent 41%)',
      borderRadius: '50%',
    },
    embossedText: {
      textShadow: '0 1px 1px rgba(0,0,0,0.3)',
      letterSpacing: '1px',
    }
  };

  return (
    <div style={styles.container}>
      <style>
        {`
          @keyframes infinityRotation {
            0% {
              transform: rotateY(0deg) rotateX(0deg);
            }
            25% {
              transform: rotateY(90deg) rotateX(20deg);
            }
            50% {
              transform: rotateY(180deg) rotateX(0deg);
            }
            75% {
              transform: rotateY(270deg) rotateX(-20deg);
            }
            100% {
              transform: rotateY(360deg) rotateX(0deg);
            }
          }

          @keyframes float {
            0%, 100% {
              transform: translateY(0);
            }
            50% {
              transform: translateY(-20px);
            }
          }
        `}
      </style>

      <div style={styles.cardWrapper}>
        <div style={styles.card}>
          {/* Front of the Card */}
          <div style={{ ...styles.cardFace, ...styles.cardFront }}>
            <img
              src="https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Visa_Inc._logo.svg/1280px-Visa_Inc._logo.svg.png"
              alt="Visa"
              style={styles.bankLogo}
            />
            <div style={styles.contactlessSymbol} />
            <div style={styles.chip} />
            <div style={{ ...styles.cardNumber, ...styles.embossedText }}>4••• •••• •••• 9012</div>
            <div style={{ ...styles.cardHolder, ...styles.embossedText }}>JOHN DOE</div>
            <div style={{ ...styles.expiryDate, ...styles.embossedText }}>VALID THRU 12/25</div>
            <div style={styles.hologram} />
            <div style={styles.glossyOverlay} />
          </div>

          {/* Back of the Card */}
          <div style={{ ...styles.cardFace, ...styles.cardBack }}>
            <div style={styles.magneticStrip} />
            <div style={styles.signatureStrip}>
              <div style={styles.cvv}>CVV 123</div>
            </div>
            <div style={{ ...styles.glossyOverlay, background: 'linear-gradient(135deg, rgba(255,255,255,0.05), rgba(255,255,255,0))' }} />
          </div>
        </div>
      </div>
    </div>
  );
};


const AdhesionSection = ({ assoId }) => {
  const { user } = useUser(); // L'utilisateur connecté (doit être un guest)
  
  const handlePayment = async () => {
    if (!user) {
      console.error("User is not logged in");
      return;
    }
    console.log("Initiating payment for 10€ adhesion");
    
    setTimeout(async () => {
      try {
        const response = await fetch(`http://localhost:8080/api/assos/5/adhesion`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ guestId: user.id.toString() })
        });
        if (response.ok) {
          console.log("Membership successful!");
        } else {
          console.error("Membership failed!");
        }
      } catch (error) {
        console.error("Error during membership:", error);
      }
    }, 1500); // délai de simulation
  };
  

  // Exemple de variant pour l'animation (vous pouvez adapter ou supprimer)
  const fadeIn = {
    hidden: { opacity: 0 },
    visible: { opacity: 1 }
  };

  return (
    <motion.section 
      initial="hidden" 
      animate="visible" 
      variants={fadeIn} 
      className="dark:bg-gray-900"
    >
      <div className="max-w-7xl mx-auto px-6">
        <h2 style={{ fontSize: '2.5rem', color: 'white', textAlign: 'center', marginBottom: '40px' }}>
          Membership
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-1 items-center">
          <div className="space-y-6 text-white dark:text-white">
            <p className="text-lg leading-relaxed font-medium">
              Join our exclusive community for just <span className="font-bold text-white dark:text-white">10€/year</span> and unlock premium benefits:
            </p>
            
            <ul className="space-y-4">
              <li className="flex items-center">
                <span className="w-6 h-6 bg-gray-800 dark:bg-white text-white dark:text-gray-900 flex items-center justify-center rounded-full mr-3">
                  ✓
                </span>
                <span className="font-medium">Premium Networking Opportunities</span>
              </li>
              <li className="flex items-center">
                <span className="w-6 h-6 bg-gray-800 dark:bg-white text-white dark:text-gray-900 flex items-center justify-center rounded-full mr-3">
                  ✓
                </span>
                <span className="font-medium">VIP Event Access</span>
              </li>
              <li className="flex items-center">
                <span className="w-6 h-6 bg-gray-800 dark:bg-white text-white dark:text-gray-900 flex items-center justify-center rounded-full mr-3">
                  ✓
                </span>
                <span className="font-medium">Professional Development Resources</span>
              </li>
            </ul>

            <div className="pt-6 space-y-4">
              <button 
                onClick={handlePayment}
                className="w-full bg-gray-900 text-white px-8 py-4 rounded-lg font-bold
                           hover:bg-gray-800 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-200
                           transition-all duration-300 shadow-lg hover:shadow-xl
                           flex items-center justify-center gap-2"
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
                </svg>
                Secure Payment - 10€/year
              </button>
              
              <p className="text-sm text-gray-500 dark:text-gray-400 text-center">
                SSL Encrypted Transaction • Cancel Anytime
              </p>
            </div>
          </div>

          <div className="relative group">
            <div className="relative">
              {/* Exemple d'un composant 3D de carte de crédit, à remplacer par le vôtre */}
              <CreditCard3D theme="monochrome" />
            </div>
          </div>
        </div>
      </div>
    </motion.section>
  );
};

export default AdhesionSection;

// Contact Section
const ContactSection = () => {
  const { register, handleSubmit, formState: { errors } } = useForm();

  const onSubmit = (data) => {
    console.log("Form Submitted:", data);
  };

  return (
    <motion.section initial="hidden" animate="visible" variants={fadeIn} className="py-12 " style={styles}>
      <div className="max-w-7xl mx-auto px-6">
        <h2 style={{ fontSize: '2.5rem', color: 'white', textAlign: 'center', marginBottom: '40px' }}>
          Contact Us
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
          <div>
          <EarthCanvas />
          </div>
          <form onSubmit={handleSubmit(onSubmit)} className=" p-8 rounded-xl shadow-lg">
            <div className="mb-4">
              <label htmlFor="name" className="block text-white mb-2">Name</label>
              <input
                id="name"
                type="text"
                {...register("name", { required: "Name is required" })}
                className={`${styles.inputField} ${errors.name ? 'border-red-500' : ''}`}
              />
              {errors.name && <p className="text-red-500 text-sm mt-1">{errors.name.message}</p>}
            </div>
            <div className="mb-4">
              <label htmlFor="email" className="block text-white mb-2">Email</label>
              <input
                id="email"
                type="email"
                {...register("email", { required: "Email is required" })}
                className={`${styles.inputField} ${errors.email ? 'border-red-500' : ''}`}
              />
              {errors.email && <p className="text-red-500 text-sm mt-1">{errors.email.message}</p>}
            </div>
            <div className="mb-4">
              <label htmlFor="message" className="block text-white mb-2">Message</label>
              <textarea
                id="message"
                rows="4"
                {...register("message", { required: "Message is required" })}
                className={`${styles.inputField} ${errors.message ? 'border-red-500' : ''}`}
              />
              {errors.message && <p className="text-red-500 text-sm mt-1">{errors.message.message}</p>}
            </div>
            <button type="submit" className="inline-flex items-center justify-center w-full px-8 py-3 text-lg font-bold text-white transition-all duration-200 bg-gray-900 border-2 border-transparent sm:w-auto rounded-xl font-pj hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-900">Send Message</button>
          </form>
        </div>
      </div>
    </motion.section>
  );
};

// Main Component
const AssociationPage = () => {
  return (
    <div className="flex flex-col space-y-20">
      <ShootingStars />
      <AboutSection />
      <TeamSection />
      <EventsSection />
      <GallerySection />
      <AdhesionSection />
      <ContactSection />
    </div>
  );
};
export { AssociationPage };