import React, { useState, useEffect } from 'react';
import { Facebook, Instagram, Mail, Phone, Twitter, ChevronDown } from 'lucide-react';

const AssociationPage = ({ 
  association = {
    name: "Nom de l'Association",
    logo: "/api/placeholder/150/150",
    background: "/api/placeholder/1920/600",
    description: "Description de l'association...",
    members: [
      { name: "John Doe", role: "Président", image: "/api/placeholder/200/200" },
      { name: "Jane Smith", role: "Trésorier", image: "/api/placeholder/200/200" }
    ],
    events: [
      {
        title: "Événement à venir",
        date: "2024-12-25",
        description: "Description de l'événement",
        image: "/api/placeholder/400/300"
      }
    ],
    gallery: [
      "/api/placeholder/400/300",
      "/api/placeholder/400/300",
      "/api/placeholder/400/300",
      "/api/placeholder/400/300",
      "/api/placeholder/400/300",
      "/api/placeholder/400/300"
    ],
    contact: {
      email: "contact@association.fr",
      phone: "01 23 45 67 89",
      social: {
        facebook: "https://facebook.com",
        twitter: "https://twitter.com",
        instagram: "https://instagram.com"
      }
    }
  }
}) => {
  const [isScrolled, setIsScrolled] = useState(false);
  const [activeSection, setActiveSection] = useState(0);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50);

      // Update active section based on scroll position
      const sections = document.querySelectorAll('section');
      sections.forEach((section, index) => {
        const rect = section.getBoundingClientRect();
        if (rect.top >= 0 && rect.top <= window.innerHeight / 2) {
          setActiveSection(index);
        }
      });
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-50 to-white">
      {/* Navigation flottante */}
      <nav className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
        isScrolled ? 'bg-white/80 backdrop-blur-lg shadow-lg' : 'bg-transparent'
      }`}>
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <img 
            src={association.logo} 
            alt="Logo" 
            className="w-12 h-12 rounded-full"
          />
          <div className="flex space-x-8">
            <a href="#team" className="text-gray-800 hover:text-blue-600 transition-colors">Équipe</a>
            <a href="#events" className="text-gray-800 hover:text-blue-600 transition-colors">Événements</a>
            <a href="#gallery" className="text-gray-800 hover:text-blue-600 transition-colors">Galerie</a>
            <a href="#join" className="text-gray-800 hover:text-blue-600 transition-colors">Adhérer</a>
          </div>
        </div>
      </nav>

      {/* Section 1: Hero Modernisée */}
      <section className="relative h-screen flex items-center justify-center overflow-hidden">
        <div 
          className="absolute inset-0 bg-cover bg-center transform scale-105 transition-transform duration-3000"
          style={{ 
            backgroundImage: `url(${association.background})`,
            transform: isScrolled ? 'scale(1.1)' : 'scale(1)'
          }}
        >
          <div className="absolute inset-0 bg-gradient-to-b from-black/60 via-black/40 to-transparent" />
        </div>
        <div className="relative container mx-auto px-4 text-center text-white">
          <img 
            src={association.logo} 
            alt="Logo"
            className="w-40 h-40 mx-auto mb-8 rounded-full ring-4 ring-white/30 shadow-2xl transform hover:scale-105 transition-transform duration-300"
          />
          <h1 className="text-6xl font-bold mb-6 tracking-tight">{association.name}</h1>
          <p className="text-xl max-w-2xl mx-auto mb-12 text-gray-200">{association.description}</p>
          <ChevronDown 
            className="w-12 h-12 mx-auto animate-bounce cursor-pointer"
            onClick={() => window.scrollTo({ top: window.innerHeight, behavior: 'smooth' })}
          />
        </div>
      </section>

      {/* Section 2: Équipe avec effet de carte 3D */}
      <section id="team" className="py-24">
        <div className="container mx-auto px-4">
          <h2 className="text-5xl font-bold text-center mb-16 bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
            Notre Équipe
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-12">
            {association.members.map((member, index) => (
              <div 
                key={index}
                className="group relative transform hover:-translate-y-2 transition-all duration-300"
              >
                <div className="absolute inset-0 bg-gradient-to-r from-blue-600 to-purple-600 rounded-2xl transform rotate-6 group-hover:rotate-4 transition-transform duration-300" />
                <div className="relative bg-white p-8 rounded-2xl shadow-xl">
                  <img 
                    src={member.image}
                    alt={member.name}
                    className="w-48 h-48 mx-auto rounded-full mb-6 object-cover ring-4 ring-blue-100"
                  />
                  <h3 className="text-2xl font-semibold text-center mb-2">{member.name}</h3>
                  <p className="text-gray-600 text-center">{member.role}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Section 3: Événements avec effet parallax */}
      <section id="events" className="py-24 bg-gray-900 text-white">
        <div className="container mx-auto px-4">
          <h2 className="text-5xl font-bold text-center mb-16">Événements à Venir</h2>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-12">
            {association.events.map((event, index) => (
              <div 
                key={index}
                className="group relative overflow-hidden rounded-2xl transform hover:-translate-y-2 transition-all duration-300"
              >
                <div className="absolute inset-0">
                  <img 
                    src={event.image}
                    alt={event.title}
                    className="w-full h-full object-cover transform group-hover:scale-110 transition-transform duration-700"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black via-black/50 to-transparent" />
                </div>
                <div className="relative p-8">
                  <div className="bg-white/10 backdrop-blur-lg rounded-lg p-6">
                    <div className="text-2xl font-bold mb-3">{event.title}</div>
                    <div className="text-blue-400 mb-4">
                      {new Date(event.date).toLocaleDateString('fr-FR', {
                        day: 'numeric',
                        month: 'long',
                        year: 'numeric'
                      })}
                    </div>
                    <p className="text-gray-200">{event.description}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Section 5: Galerie modernisée */}
      <section id="gallery" className="py-24">
        <div className="container mx-auto px-4">
          <h2 className="text-5xl font-bold text-center mb-16 bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
            Galerie
          </h2>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            {association.gallery.map((image, index) => (
              <div 
                key={index}
                className="group relative aspect-square overflow-hidden rounded-2xl cursor-pointer"
              >
                <img 
                  src={image}
                  alt={`Gallery ${index + 1}`}
                  className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-110"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/80 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Section 6: Formulaire d'adhésion moderne */}
      <section id="join" className="py-24 bg-gradient-to-br from-blue-600 to-purple-600 text-white">
        <div className="container mx-auto px-4">
          <h2 className="text-5xl font-bold text-center mb-16">Rejoignez-nous</h2>
          <form className="max-w-xl mx-auto backdrop-blur-lg bg-white/10 p-8 rounded-2xl shadow-2xl">
            <div className="space-y-6">
              <div>
                <label className="block text-sm font-medium mb-2">Nom complet</label>
                <input
                  type="text"
                  className="w-full px-4 py-3 bg-white/5 border border-white/20 rounded-lg focus:outline-none focus:ring-2 focus:ring-white/50 transition-all placeholder-white/50"
                  placeholder="Votre nom"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-2">Email</label>
                <input
                  type="email"
                  className="w-full px-4 py-3 bg-white/5 border border-white/20 rounded-lg focus:outline-none focus:ring-2 focus:ring-white/50 transition-all placeholder-white/50"
                  placeholder="votre@email.com"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-2">Téléphone</label>
                <input
                  type="tel"
                  className="w-full px-4 py-3 bg-white/5 border border-white/20 rounded-lg focus:outline-none focus:ring-2 focus:ring-white/50 transition-all placeholder-white/50"
                  placeholder="Votre téléphone"
                />
              </div>
              <button
                type="submit"
                className="w-full bg-white text-blue-600 py-4 rounded-lg font-semibold hover:bg-blue-50 transition-colors duration-300"
              >
                Adhérer maintenant
              </button>
            </div>
          </form>
        </div>
      </section>

      {/* Section finale: Contact et Réseaux sociaux moderne */}
      <section className="py-24 bg-gray-900 text-white">
        <div className="container mx-auto px-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-16">
            <div className="backdrop-blur-lg bg-white/5 p-8 rounded-2xl">
              <h2 className="text-3xl font-bold mb-8">Contactez-nous</h2>
              <div className="space-y-6">
                <div className="flex items-center space-x-4 group">
                  <div className="p-4 bg-blue-600 rounded-lg group-hover:bg-blue-500 transition-colors">
                    <Mail className="w-6 h-6" />
                  </div>
                  <span className="text-lg">{association.contact.email}</span>
                </div>
                <div className="flex items-center space-x-4 group">
                  <div className="p-4 bg-blue-600 rounded-lg group-hover:bg-blue-500 transition-colors">
                    <Phone className="w-6 h-6" />
                  </div>
                  <span className="text-lg">{association.contact.phone}</span>
                </div>
              </div>
            </div>
            <div className="backdrop-blur-lg bg-white/5 p-8 rounded-2xl">
              <h2 className="text-3xl font-bold mb-8">Suivez-nous</h2>
              <div className="flex space-x-6">
                {Object.entries(association.contact.social).map(([platform, url]) => (
                  <a
                    key={platform}
                    href={url}
                    className="p-4 bg-blue-600 rounded-lg hover:bg-blue-500 transition-colors"
                  >
                    {platform === 'facebook' && <Facebook className="w-6 h-6" />}
                    {platform === 'twitter' && <Twitter className="w-6 h-6" />}
                    {platform === 'instagram' && <Instagram className="w-6 h-6" />}
                  </a>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};

export {AssociationPage};