import axios from 'axios';
import React, { useState, useEffect } from 'react';
import { useUser } from '../../contexts/UserContext';
import { useNavigate } from 'react-router-dom';
import { useDropzone } from 'react-dropzone';

const Profile = () => {
  const { user, setUser } = useUser();
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

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

  useEffect(() => {
    if (!loading && !user) {
      navigate('/login');
    }
  }, [loading, user, navigate]);

  if (loading || !user) return <div style={styles.loading}>Chargement...</div>;

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

// Composant de téléversement de photo (drag & drop)
const PhotoUpload = ({ userId, onUploadSuccess }) => {
  const [uploading, setUploading] = useState(false);

  const onDrop = async (acceptedFiles) => {
    if (acceptedFiles && acceptedFiles.length > 0) {
      const file = acceptedFiles[0];
      const formData = new FormData();
      formData.append('file', file);
      setUploading(true);
      try {
        const response = await axios.post(`/api/users/${userId}/photo`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        onUploadSuccess(response.data);
      } catch (err) {
        console.error('Erreur de téléversement :', err);
      } finally {
        setUploading(false);
      }
    }
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop });

  return (
    <div {...getRootProps()} style={dropzoneStyles.container}>
      <input {...getInputProps()} />
      {isDragActive ? (
        <p>Déposez la photo ici ...</p>
      ) : (
        <p>Glissez-déposez votre photo ici, ou cliquez pour sélectionner un fichier</p>
      )}
      {uploading && <p>Téléversement en cours...</p>}
    </div>
  );
};

// Composant pour le profil Guest
const GuestProfile = ({ user, setUser }) => {
  // Parser le champ socialMedia en objet JSON
  let social = { instagram: '', facebook: '', linkedin: '' };
  try {
    if (user.socialMedia) {
      social = JSON.parse(user.socialMedia);
    }
  } catch (error) {
    console.error("Erreur parsing socialMedia:", error);
  }

  const [editMode, setEditMode] = useState(false);
  const [formData, setFormData] = useState({
    name: user.name || '',
    email: user.email || '',
    dateNaissance: user.dateNaissance || '',
    statut: user.statut || 'invité',
    promo: user.promo || '',
    photo: user.photo || '',
    instagram: social.instagram || '',
    facebook: social.facebook || '',
    linkedin: social.linkedin || '',
  });

  // Fonction pour calculer l'âge à partir de la date de naissance
  const calculateAge = (birthDateStr) => {
    if (!birthDateStr) return '';
    const birthDate = new Date(birthDateStr);
    const diffMs = Date.now() - birthDate.getTime();
    const ageDt = new Date(diffMs);
    return Math.abs(ageDt.getUTCFullYear() - 1970);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      // Si le statut n'est pas "étudiant", on définit promo comme étant le statut
      const promoValue = formData.statut === 'étudiant' ? formData.promo : formData.statut;
      // Recomposer socialMedia en JSON
      const socialMediaJson = JSON.stringify({
        instagram: formData.instagram,
        facebook: formData.facebook,
        linkedin: formData.linkedin,
      });
      const payload = {
        ...formData,
        promo: promoValue,
        socialMedia: socialMediaJson,
        role: 'GUEST'
      };
      const response = await axios.put(`/api/users/${user.id}`, payload, {
        headers: { Authorization: `Bearer ${localStorage.getItem("token")}` },
      });
      setUser(response.data);
      setEditMode(false);
    } catch (error) {
      console.error('Erreur de mise à jour:', error);
    }
  };

  const handlePhotoUpload = (updatedUser) => {
    console.log("Photo mise à jour:", updatedUser.photo);
    setUser(updatedUser);
    setFormData(prev => ({ ...prev, photo: updatedUser.photo }));
  };

  return (
    <div style={styles.profileCard}>
      <div style={styles.avatarSection}>
        <img src={user.photo || '/default-avatar.png'} alt="Profil" style={styles.avatar} />
        <PhotoUpload userId={user.id} onUploadSuccess={handlePhotoUpload} />
      </div>

      {editMode ? (
        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.formGroup}>
            <label style={styles.label}>Nom complet</label>
            <input
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.formGroup}>
            <label style={styles.label}>Email</label>
            <input
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.formGroup}>
            <label style={styles.label}>Date de naissance</label>
            <input
              type="date"
              value={formData.dateNaissance}
              onChange={(e) => setFormData({ ...formData, dateNaissance: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.formRow}>
            <div style={styles.formGroup}>
              <label style={styles.label}>Statut</label>
              <select
                value={formData.statut}
                onChange={(e) => setFormData({ ...formData, statut: e.target.value })}
                style={styles.input}
              >
                <option value="étudiant">Étudiant</option>
                <option value="prof">Prof</option>
                <option value="invité">Invité</option>
                <option value="asso">Asso</option>
              </select>
            </div>
            {formData.statut === 'étudiant' && (
              <div style={styles.formGroup}>
                <label style={styles.label}>Promo</label>
                <select
                  value={formData.promo}
                  onChange={(e) => setFormData({ ...formData, promo: e.target.value })}
                  style={styles.input}
                >
                  <option value="1A">1A</option>
                  <option value="2A">2A</option>
                  <option value="3A">3A</option>
                  <option value="4A">4A</option>
                  <option value="5A">5A</option>
                  <option value="ancien">Ancien</option>
                </select>
              </div>
            )}
          </div>
          <div style={styles.formRow}>
            <div style={styles.formGroup}>
              <label style={styles.label}>Instagram</label>
              <input
                value={formData.instagram}
                onChange={(e) => setFormData({ ...formData, instagram: e.target.value })}
                style={styles.input}
              />
            </div>
            <div style={styles.formGroup}>
              <label style={styles.label}>Facebook</label>
              <input
                value={formData.facebook}
                onChange={(e) => setFormData({ ...formData, facebook: e.target.value })}
                style={styles.input}
              />
            </div>
            <div style={styles.formGroup}>
              <label style={styles.label}>LinkedIn</label>
              <input
                value={formData.linkedin}
                onChange={(e) => setFormData({ ...formData, linkedin: e.target.value })}
                style={styles.input}
              />
            </div>
          </div>
          <div style={styles.buttonContainer}>
            <button type="submit" style={styles.primaryButton}>Enregistrer</button>
            <button type="button" onClick={() => setEditMode(false)} style={styles.secondaryButton}>Annuler</button>
          </div>
        </form>
      ) : (
        <>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Nom :</span>
            <span style={styles.infoValue}>{user.name}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Email :</span>
            <span style={styles.infoValue}>{user.email}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Âge :</span>
            <span style={styles.infoValue}>{calculateAge(user.dateNaissance)} ans</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Statut :</span>
            <span style={styles.infoValue}>{user.statut}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Promo :</span>
            <span style={styles.infoValue}>{user.promo}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Instagram :</span>
            <span style={styles.infoValue}>
              {user.socialMedia ? JSON.parse(user.socialMedia).instagram : ''}
            </span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Facebook :</span>
            <span style={styles.infoValue}>
              {user.socialMedia ? JSON.parse(user.socialMedia).facebook : ''}
            </span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>LinkedIn :</span>
            <span style={styles.infoValue}>
              {user.socialMedia ? JSON.parse(user.socialMedia).linkedin : ''}
            </span>
          </div>
          <button onClick={() => setEditMode(true)} style={styles.editButton}>
            Modifier le profil
          </button>
        </>
      )}
    </div>
  );
};

// AssoProfile pour les associations
const AssoProfile = ({ user, setUser }) => {
  let social = { instagram: '', facebook: '', linkedin: '' };
  try {
    if (user.socialMedia) {
      social = JSON.parse(user.socialMedia);
    }
  } catch (error) {
    console.error("Erreur parsing socialMedia:", error);
  }

  const [editMode, setEditMode] = useState(false);
  const [formData, setFormData] = useState({
    name: user.name || '',
    photo: user.photo || '',
    bgPhoto: user.bgPhoto || '',
    adhesionPrice: user.adhesionPrice || 0,
    description: user.description || '',
    rib: user.rib || '',
    instagram: social.instagram || '',
    facebook: social.facebook || '',
    linkedin: social.linkedin || '',
  });

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const socialMediaJson = JSON.stringify({
        instagram: formData.instagram,
        facebook: formData.facebook,
        linkedin: formData.linkedin,
      });
      const payload = {
        ...formData,
        socialMedia: socialMediaJson,
        role: 'ASSO'
      };
      const response = await axios.put(`/api/users/${user.id}`, payload, {
        headers: { Authorization: `Bearer ${localStorage.getItem("token")}` },
      });
      setUser(response.data);
      setEditMode(false);
    } catch (error) {
      console.error('Erreur de mise à jour:', error);
    }
  };

  return (
    <div style={styles.assoContainer}>
      <div style={{ ...styles.assoHeader, backgroundImage: `url(${user.bgPhoto || '/default-banner.jpg'})` }}>
        <img src={user.photo || '/default-logo.png'} alt="Logo" style={styles.assoLogo} />
      </div>
      {editMode ? (
        <form onSubmit={handleSubmit} style={styles.assoForm}>
          <div style={styles.formGroup}>
            <label style={styles.label}>Nom de l'association</label>
            <input
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.formRow}>
            <div style={styles.formGroup}>
              <label style={styles.label}>Logo (URL)</label>
              <input
                value={formData.photo}
                onChange={(e) => setFormData({ ...formData, photo: e.target.value })}
                style={styles.input}
              />
            </div>
            <div style={styles.formGroup}>
              <label style={styles.label}>Bannière (URL)</label>
              <input
                value={formData.bgPhoto}
                onChange={(e) => setFormData({ ...formData, bgPhoto: e.target.value })}
                style={styles.input}
              />
            </div>
          </div>
          <div style={styles.formGroup}>
            <label style={styles.label}>Prix d'adhésion (€)</label>
            <input
              type="number"
              value={formData.adhesionPrice}
              onChange={(e) => setFormData({ ...formData, adhesionPrice: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.formGroup}>
            <label style={styles.label}>Description</label>
            <textarea
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              style={styles.textarea}
              rows="4"
            />
          </div>
          <div style={styles.formGroup}>
            <label style={styles.label}>RIB</label>
            <input
              value={formData.rib}
              onChange={(e) => setFormData({ ...formData, rib: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.formRow}>
            <div style={styles.formGroup}>
              <label style={styles.label}>Instagram</label>
              <input
                value={formData.instagram}
                onChange={(e) => setFormData({ ...formData, instagram: e.target.value })}
                style={styles.input}
              />
            </div>
            <div style={styles.formGroup}>
              <label style={styles.label}>Facebook</label>
              <input
                value={formData.facebook}
                onChange={(e) => setFormData({ ...formData, facebook: e.target.value })}
                style={styles.input}
              />
            </div>
            <div style={styles.formGroup}>
              <label style={styles.label}>LinkedIn</label>
              <input
                value={formData.linkedin}
                onChange={(e) => setFormData({ ...formData, linkedin: e.target.value })}
                style={styles.input}
              />
            </div>
          </div>
          <div style={styles.buttonContainer}>
            <button type="submit" style={styles.primaryButton}>Enregistrer</button>
            <button type="button" onClick={() => setEditMode(false)} style={styles.secondaryButton}>Annuler</button>
          </div>
        </form>
      ) : (
        <div style={styles.assoContent}>
          <h2 style={styles.assoName}>{user.name}</h2>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Prix d'adhésion :</span>
            <span style={styles.infoValue}>{user.adhesionPrice} €</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>RIB :</span>
            <span style={styles.infoValue}>{user.rib}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Description :</span>
            <span style={styles.infoValue}>{user.description}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Instagram :</span>
            <span style={styles.infoValue}>{user.socialMedia ? JSON.parse(user.socialMedia).instagram : ''}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>Facebook :</span>
            <span style={styles.infoValue}>{user.socialMedia ? JSON.parse(user.socialMedia).facebook : ''}</span>
          </div>
          <div style={styles.infoItem}>
            <span style={styles.infoLabel}>LinkedIn :</span>
            <span style={styles.infoValue}>{user.socialMedia ? JSON.parse(user.socialMedia).linkedin : ''}</span>
          </div>
          <button onClick={() => setEditMode(true)} style={styles.editButton}>Modifier le profil</button>
        </div>
      )}
    </div>
  );
};

// Styles communs
const styles = {
  container: {
    marginTop: '6rem',
    borderRadius: '15px',
    marginBottom: '2rem',
    maxWidth: '1200px',
    margin: '0 auto',
    padding: '2rem',
    background: 'linear-gradient(to bottom, #f8f9fa, #e9ecef)',
    minHeight: '100vh',
    fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif"
  },
  header: {
    fontSize: '2.4rem',
    color: '#2c3e50',
    marginBottom: '2rem',
    textAlign: 'center',
    borderBottom: '2px solid #3498db',
    paddingBottom: '0.5rem'
  },
  loading: {
    textAlign: 'center',
    fontSize: '1.2rem',
    padding: '3rem',
    color: '#666'
  },
  profileCard: {
    backgroundColor: '#fff',
    borderRadius: '15px',
    padding: '2rem',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
    maxWidth: '700px',
    margin: '1rem auto'
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
  form: {
    marginTop: '1rem'
  },
  formRow: {
    display: 'flex',
    gap: '1.5rem',
    marginBottom: '1.5rem'
  },
  formGroup: {
    marginBottom: '1.5rem',
    flex: 1,
    display: 'flex',
    flexDirection: 'column'
  },
  label: {
    marginBottom: '0.5rem',
    color: '#34495e',
    fontWeight: '600'
  },
  input: {
    padding: '0.8rem',
    border: '1px solid #ddd',
    borderRadius: '8px',
    fontSize: '1rem',
    outline: 'none',
    transition: 'border 0.2s'
  },
  textarea: {
    padding: '0.8rem',
    border: '1px solid #ddd',
    borderRadius: '8px',
    fontSize: '1rem',
    outline: 'none',
    transition: 'border 0.2s',
    resize: 'vertical'
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
    fontSize: '1rem'
  },
  secondaryButton: {
    backgroundColor: '#95a5a6',
    color: 'white',
    padding: '0.8rem 1.5rem',
    borderRadius: '8px',
    border: 'none',
    cursor: 'pointer',
    fontSize: '1rem'
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
    color: '#2c3e50',
    minWidth: '120px'
  },
  infoValue: {
    color: '#7f8c8d',
    textAlign: 'right'
  },
  socialLinks: {
    display: 'flex',
    justifyContent: 'center',
    gap: '1rem',
    margin: '1rem 0'
  },
  socialIcon: {
    width: '30px',
    height: '30px'
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
    fontSize: '1rem'
  },
  // Styles pour Association
  assoContainer: {
    backgroundColor: '#fff',
    borderRadius: '15px',
    overflow: 'hidden',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
    margin: '1rem auto'
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
    boxShadow: '0 2px 6px rgba(0, 0, 0, 0.15)'
  },
  assoContent: {
    padding: '2rem',
    marginTop: '80px'
  },
  assoName: {
    fontSize: '2rem',
    color: '#2c3e50',
    marginBottom: '1rem'
  },
  infoSection: {
    margin: '1.5rem 0'
  },
  descriptionBox: {
    backgroundColor: '#ecf0f1',
    padding: '1.5rem',
    borderRadius: '10px',
    marginBottom: '1.5rem'
  },
  sectionTitle: {
    fontSize: '1.5rem',
    marginBottom: '0.5rem',
    color: '#2c3e50'
  },
  descriptionText: {
    color: '#7f8c8d',
    lineHeight: '1.6'
  }
};

// Styles spécifiques pour le dropzone
const dropzoneStyles = {
  container: {
    border: '2px dashed #ccc',
    padding: '20px',
    textAlign: 'center',
    borderRadius: '8px',
    marginTop: '1rem',
    cursor: 'pointer'
  }
};

export default Profile;
