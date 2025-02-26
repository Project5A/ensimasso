import axios from 'axios';
import React, { useState, useEffect } from 'react';
import { useUser } from '../../contexts/UserContext';

const EventsDashboard = () => {
  const { user } = useUser();
  const [events, setEvents] = useState([]);
  const [editingEventId, setEditingEventId] = useState(null);
  const [formData, setFormData] = useState({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchEvents = async () => {
      try {
        const response = await axios.get('/api/events');
        let userEvents = [];
        
        if (user.role === 'ASSO') {// Pour un GUEST
          userEvents = response.data.filter(event => 
            event.participants?.some(p => p.id === user.id)
          );
        } else if (user.role === 'GUEST') {
          userEvents = response.data.filter(
            (event) => 
              event.participants?.some(
                (participant) => participant.id === user.id
              )
          );
        }
        
        setEvents(userEvents);
      } catch (error) {
        console.error("Erreur lors de la récupération des événements :", error);
      } finally {
        setLoading(false);
      }
    };
  
    fetchEvents();
  }, [user]);  
  
  // Les fonctions handleEdit, handleCancel, handleChange, handleSave, handleDelete restent inchangées.
  // ...
  
  if (loading) return <div style={styles.loading}>Chargement des événements...</div>;

  return (
    <div style={styles.container}>
      <div style={styles.content}>
        <h1 style={styles.title}>Mes Événements</h1>
        {events.length === 0 ? (
          <p style={styles.text}>Aucun événement à afficher.</p>
        ) : (
          events.map(event => (
            <div key={event.id} style={styles.eventCard}>
              {editingEventId === event.id ? (
                <div>
                  {/* Formulaire de modification */}
                  <input
                    type="text"
                    name="title"
                    placeholder="Titre"
                    value={formData.title}
                    onChange={handleChange}
                    style={styles.input}
                  />
                  <input
                    type="date"
                    name="date"
                    placeholder="Date"
                    value={formData.date}
                    onChange={handleChange}
                    style={styles.input}
                  />
                  <input
                    type="text"
                    name="location"
                    placeholder="Lieu"
                    value={formData.location}
                    onChange={handleChange}
                    style={styles.input}
                  />
                  <textarea
                    name="description"
                    placeholder="Description"
                    value={formData.description}
                    onChange={handleChange}
                    style={styles.textarea}
                  />
                  <input
                    type="number"
                    name="adhPrice"
                    placeholder="Prix adhésion"
                    value={formData.adhPrice}
                    onChange={handleChange}
                    style={styles.input}
                  />
                  <input
                    type="number"
                    name="nonAdhPrice"
                    placeholder="Prix non adhésion"
                    value={formData.nonAdhPrice}
                    onChange={handleChange}
                    style={styles.input}
                  />
                  <input
                    type="text"
                    name="eventImage"
                    placeholder="URL de l'image"
                    value={formData.eventImage}
                    onChange={handleChange}
                    style={styles.input}
                  />
                  <div style={styles.buttonContainer}>
                    <button onClick={() => handleSave(event.id)} style={styles.primaryButton}>
                      Sauvegarder
                    </button>
                    <button onClick={handleCancel} style={styles.secondaryButton}>
                      Annuler
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  <h2 style={styles.eventTitle}>{event.title}</h2>
                  <p style={styles.eventDate}>Date : {event.date}</p>
                  <p style={styles.eventLocation}>Lieu : {event.location}</p>
                  <p style={styles.eventDescription}>{event.description}</p>
                  <p style={styles.eventPrice}>
                    Adhésion : {event.adhPrice}€ - Non adhésion : {event.nonAdhPrice}€
                  </p>
                  {event.eventImage && (
                    <img src={event.eventImage} alt="Événement" style={styles.eventImage} />
                  )}
                  <div style={styles.buttonContainer}>
                    <button onClick={() => handleEdit(event)} style={styles.primaryButton}>
                      Modifier
                    </button>
                    <button onClick={() => handleDelete(event.id)} style={styles.secondaryButton}>
                      Supprimer
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
};

const styles = {
  container: {
    padding: '2rem',
    backgroundColor: '#f0f2f5',
    minHeight: '100vh',
    fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif"
  },
  content: {
    flex: 1
  },
  title: {
    fontSize: '2.5rem',
    textAlign: 'center',
    marginBottom: '2rem',
    color: '#333'
  },
  loading: {
    textAlign: 'center',
    padding: '2rem',
    fontSize: '1.2rem',
    color: '#666'
  },
  text: {
    textAlign: 'center',
    fontSize: '1.2rem',
    color: '#666'
  },
  eventCard: {
    backgroundColor: '#fff',
    borderRadius: '10px',
    padding: '1.5rem',
    marginBottom: '1rem',
    boxShadow: '0 2px 6px rgba(0, 0, 0, 0.1)'
  },
  eventTitle: {
    fontSize: '1.8rem',
    marginBottom: '0.5rem',
    color: '#333'
  },
  eventDate: {
    fontSize: '1rem',
    color: '#888',
    marginBottom: '0.5rem'
  },
  eventLocation: {
    fontSize: '1.2rem',
    color: '#555',
    marginBottom: '0.5rem'
  },
  eventDescription: {
    fontSize: '1.2rem',
    color: '#555',
    marginBottom: '0.5rem'
  },
  eventPrice: {
    fontSize: '1rem',
    color: '#888',
    marginBottom: '1rem'
  },
  eventImage: {
    width: '100%',
    borderRadius: '8px',
    marginBottom: '1rem'
  },
  input: {
    width: '100%',
    padding: '0.8rem',
    marginBottom: '1rem',
    border: '1px solid #ccc',
    borderRadius: '8px',
    fontSize: '1rem'
  },
  textarea: {
    width: '100%',
    padding: '0.8rem',
    marginBottom: '1rem',
    border: '1px solid #ccc',
    borderRadius: '8px',
    fontSize: '1rem',
    minHeight: '100px'
  },
  buttonContainer: {
    display: 'flex',
    gap: '1rem'
  },
  primaryButton: {
    flex: 1,
    backgroundColor: '#3498db',
    color: '#fff',
    border: 'none',
    padding: '0.8rem',
    borderRadius: '8px',
    cursor: 'pointer',
    fontSize: '1rem'
  },
  secondaryButton: {
    flex: 1,
    backgroundColor: '#e74c3c',
    color: '#fff',
    border: 'none',
    padding: '0.8rem',
    borderRadius: '8px',
    cursor: 'pointer',
    fontSize: '1rem'
  }
};

export default EventsDashboard;
