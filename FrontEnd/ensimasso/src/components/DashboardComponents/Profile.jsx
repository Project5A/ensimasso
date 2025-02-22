import axios from 'axios';
import React, { useState, useEffect } from 'react';
import { useUser } from '../../contexts/UserContext';

const Profile = () => {
  const { user, setUser } = useUser();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchUserData = async () => {
      try {
        const response = await axios.get('/api/users/me', {
          headers: {
            Authorization: `Bearer ${localStorage.getItem('token')}`
          }
        });
        setUser(response.data);
      } catch (error) {
        console.error('Error fetching user:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchUserData();
  }, [setUser]);

  if (loading) return <div style={styles.loading}>Chargement...</div>;
  if (!user) return <div style={styles.error}>Veuillez vous connecter</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>
        {user.role === 'ASSO' ? 'Profil Association' : 'Profil Étudiant'}
      </h1>

      {user.role === 'ASSO' ? (
        <AssoProfile user={user} setUser={setUser} />
      ) : (
        <GuestProfile user={user} setUser={setUser} />
      )}
    </div>
  );
};

// Profil Étudiant
const GuestProfile = ({ user, setUser }) => {
  const [editMode, setEditMode] = useState(false);
  const [formData, setFormData] = useState({
    name: user.name || '',
    age: user.age || '',
    email: user.email || '',
    promo: user.promo || '',
    photo: user.photo || '',
    instagram: user.instagram || '',
    linkedin: user.linkedin || ''
  });

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await axios.put(`/api/users/${user.id}`, {
        ...formData,
        role: 'GUEST'
      });
      setUser(response.data);
      setEditMode(false);
    } catch (error) {
      console.error('Erreur de mise à jour:', error);
    }
  };

  return (
    <div style={styles.profileCard}>
      <div style={styles.avatarSection}>
        <img 
          src={user.photo || '/default-avatar.png'} 
          alt="Profil" 
          style={styles.avatar}
        />
      </div>

      {editMode ? (
        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.formGroup}>
            <label>Nom complet</label>
            <input
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              style={styles.input}
            />
          </div>

          <div style={styles.formRow}>
            <div style={styles.formGroup}>
              <label>Âge</label>
              <input
                type="number"
                value={formData.age}
                onChange={(e) => setFormData({ ...formData, age: e.target.value })}
                style={styles.input}
              />
            </div>

            <div style={styles.formGroup}>
              <label>Promotion</label>
              <input
                value={formData.promo}
                onChange={(e) => setFormData({ ...formData, promo: e.target.value })}
                style={styles.input}
              />
            </div>
          </div>

          <div style={styles.formGroup}>
            <label>Photo de profil (URL)</label>
            <input
              value={formData.photo}
              onChange={(e) => setFormData({ ...formData, photo: e.target.value })}
              style={styles.input}
            />
          </div>

          <div style={styles.buttonContainer}>
            <button type="submit" style={styles.primaryButton}>Enregistrer</button>
            <button 
              type="button" 
              onClick={() => setEditMode(false)} 
              style={styles.secondaryButton}
            >
              Annuler
            </button>
          </div>
        </form>
      ) : (
        <>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Nom:</span>
            <span style={styles.infoValue}>{user.name}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Âge:</span>
            <span style={styles.infoValue}>{user.age}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Email:</span>
            <span style={styles.infoValue}>{user.email}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Promotion:</span>
            <span style={styles.infoValue}>{user.promo}</span>
          </div>
          
          <div style={styles.socialLinks}>
            {user.instagram && (
              <a href={user.instagram} target="_blank" rel="noopener noreferrer">
                <img src="/instagram-icon.png" alt="Instagram" style={styles.socialIcon} />
              </a>
            )}
            {user.linkedin && (
              <a href={user.linkedin} target="_blank" rel="noopener noreferrer">
                <img src="/linkedin-icon.png" alt="LinkedIn" style={styles.socialIcon} />
              </a>
            )}
          </div>

          <button 
            onClick={() => setEditMode(true)}
            style={styles.editButton}
          >
            Modifier le profil
          </button>
        </>
      )}
    </div>
  );
};

// Profil Association
const AssoProfile = ({ user, setUser }) => {
  const [editMode, setEditMode] = useState(false);
  const [formData, setFormData] = useState({
    name: user.name || '',
    photo: user.photo || '',
    bgPhoto: user.bgPhoto || '',
    adhesionPrice: user.adhesionPrice || 0,
    description: user.description || '',
    socialMedia: user.socialMedia || '',
    rib: user.rib || ''
  });

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await axios.put(`/api/users/${user.id}`, {
        ...formData,
        role: 'ASSO'
      });
      setUser(response.data);
      setEditMode(false);
    } catch (error) {
      console.error('Erreur de mise à jour:', error);
    }
  };

  return (
    <div style={styles.assoContainer}>
      <div 
        style={{ 
          ...styles.assoHeader,
          backgroundImage: `url(${user.bgPhoto || '/default-banner.jpg'})`
        }}
      >
        <img
          src={user.photo || '/default-logo.png'}
          alt="Logo"
          style={styles.assoLogo}
        />
      </div>

      {editMode ? (
        <form onSubmit={handleSubmit} style={styles.assoForm}>
          <div style={styles.formGroup}>
            <label>Nom de l'association</label>
            <input
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              style={styles.input}
            />
          </div>

          <div style={styles.formRow}>
            <div style={styles.formGroup}>
              <label>Logo (URL)</label>
              <input
                value={formData.photo}
                onChange={(e) => setFormData({ ...formData, photo: e.target.value })}
                style={styles.input}
              />
            </div>
            
            <div style={styles.formGroup}>
              <label>Bannière (URL)</label>
              <input
                value={formData.bgPhoto}
                onChange={(e) => setFormData({ ...formData, bgPhoto: e.target.value })}
                style={styles.input}
              />
            </div>
          </div>

          <div style={styles.formGroup}>
            <label>Prix d'adhésion (€)</label>
            <input
              type="number"
              value={formData.adhesionPrice}
              onChange={(e) => setFormData({ ...formData, adhesionPrice: e.target.value })}
              style={styles.input}
            />
          </div>

          <div style={styles.formGroup}>
            <label>Description</label>
            <textarea
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              style={styles.textarea}
              rows="4"
            />
          </div>

          <div style={styles.buttonContainer}>
            <button type="submit" style={styles.primaryButton}>Enregistrer</button>
            <button 
              type="button" 
              onClick={() => setEditMode(false)} 
              style={styles.secondaryButton}
            >
              Annuler
            </button>
          </div>
        </form>
      ) : (
        <div style={styles.assoContent}>
          <h2 style={styles.assoName}>{user.name}</h2>
          
          <div style={styles.infoSection}>
            <div style={styles.infoItem}>
              <span style={styles.infoLabel}>Prix d'adhésion:</span>
              <span style={styles.infoValue}>{user.adhesionPrice} €</span>
            </div>
            
            <div style={styles.infoItem}>
              <span style={styles.infoLabel}>RIB:</span>
              <span style={styles.infoValue}>{user.rib}</span>
            </div>
            
            <div style={styles.infoItem}>
              <span style={styles.infoLabel}>Réseaux sociaux:</span>
              <span style={styles.infoValue}>{user.socialMedia}</span>
            </div>
          </div>

          <div style={styles.descriptionBox}>
            <h3 style={styles.sectionTitle}>Description</h3>
            <p style={styles.descriptionText}>{user.description}</p>
          </div>

          <button 
            onClick={() => setEditMode(true)}
            style={styles.editButton}
          >
            Modifier le profil
          </button>
        </div>
      )}
    </div>
  );
};

// Styles communs
const styles = {
  container: {
    maxWidth: '1200px',
    margin: '0 auto',
    padding: '2rem',
    backgroundColor: '#f8f9fa',
    minHeight: '100vh'
  },
  header: {
    fontSize: '2.2rem',
    color: '#2c3e50',
    marginBottom: '2rem',
    textAlign: 'center'
  },
  loading: {
    textAlign: 'center',
    fontSize: '1.2rem',
    padding: '3rem',
    color: '#666'
  },
  error: {
    textAlign: 'center',
    color: '#e74c3c',
    padding: '3rem',
    fontSize: '1.2rem'
  },
  // Styles pour Étudiant
  profileCard: {
    backgroundColor: 'white',
    borderRadius: '15px',
    padding: '2rem',
    boxShadow: '0 4px 6px rgba(0, 0, 0, 0.05)',
    maxWidth: '700px',
    margin: '0 auto'
  },
  avatarSection: {
    textAlign: 'center',
    marginBottom: '2rem'
  },
  avatar: {
    width: '150px',
    height: '150px',
    borderRadius: '50%',
    objectFit: 'cover',
    border: '3px solid #3498db'
  },
  formRow: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: '1.5rem',
    marginBottom: '1.5rem'
  },
  // Styles pour Association
  assoContainer: {
    backgroundColor: 'white',
    borderRadius: '15px',
    overflow: 'hidden',
    boxShadow: '0 4px 6px rgba(0, 0, 0, 0.05)'
  },
  assoHeader: {
    height: '250px',
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    position: 'relative'
  },
  assoLogo: {
    width: '120px',
    height: '120px',
    borderRadius: '15px',
    position: 'absolute',
    bottom: '-60px',
    left: '2rem',
    border: '3px solid white',
    boxShadow: '0 2px 4px rgba(0, 0, 0, 0.1)'
  },
  assoContent: {
    padding: '2rem',
    marginTop: '80px'
  },
  // Styles communs
  formGroup: {
    marginBottom: '1.5rem'
  },
  input: {
    width: '100%',
    padding: '0.8rem',
    border: '1px solid #ddd',
    borderRadius: '8px',
    fontSize: '1rem'
  },
  textarea: {
    width: '100%',
    padding: '0.8rem',
    border: '1px solid #ddd',
    borderRadius: '8px',
    fontSize: '1rem',
    minHeight: '100px'
  },
  buttonContainer: {
    display: 'flex',
    gap: '1rem',
    marginTop: '2rem'
  },
  primaryButton: {
    backgroundColor: '#3498db',
    color: 'white',
    padding: '0.8rem 1.5rem',
    borderRadius: '8px',
    border: 'none',
    cursor: 'pointer',
    fontSize: '1rem',
    transition: 'background-color 0.2s',
    '&:hover': {
      backgroundColor: '#2980b9'
    }
  },
  secondaryButton: {
    backgroundColor: '#95a5a6',
    color: 'white',
    padding: '0.8rem 1.5rem',
    borderRadius: '8px',
    border: 'none',
    cursor: 'pointer',
    fontSize: '1rem',
    transition: 'background-color 0.2s',
    '&:hover': {
      backgroundColor: '#7f8c8d'
    }
  },
  infoItem: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '1rem 0',
    borderBottom: '1px solid #eee'
  },
  infoLabel: {
    fontWeight: '600',
    color: '#0000',
    minWidth: '120px'
  },
  infoValue: {
    color: '#7f8c8d',
    textAlign: 'right'
  },
  editButton: {
    width: '100%',
    marginTop: '1.5rem',
    backgroundColor: '#27ae60',
    color: 'white',
    padding: '1rem',
    borderRadius: '8px',
    border: 'none',
    cursor: 'pointer',
    fontSize: '1rem',
    transition: 'background-color 0.2s',
    '&:hover': {
      backgroundColor: '#219a52'
    }
  }
};

export default Profile;