import { useState, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import './CreateAssociationForm.css';

const CreateAssociationForm = () => {
  const [name, setName] = useState('');
  const [logo, setLogo] = useState(null);
  const [background, setBackground] = useState(null);
  const [description, setDescription] = useState('');
  const [members, setMembers] = useState('');
  const [gallery, setGallery] = useState([]);
  const [contacts, setContacts] = useState('');

  // Dropzone pour le logo
  const onDropLogo = useCallback((acceptedFiles) => {
    if (acceptedFiles.length > 0) {
      setLogo(acceptedFiles[0]);
    }
  }, []);

  const {
    getRootProps: getRootPropsLogo,
    getInputProps: getInputPropsLogo,
    isDragActive: isDragActiveLogo,
  } = useDropzone({
    onDrop: onDropLogo,
    accept: 'image/*',
    multiple: false,
  });

  // Dropzone pour l'arrière-plan
  const onDropBackground = useCallback((acceptedFiles) => {
    if (acceptedFiles.length > 0) {
      setBackground(acceptedFiles[0]);
    }
  }, []);

  const {
    getRootProps: getRootPropsBackground,
    getInputProps: getInputPropsBackground,
    isDragActive: isDragActiveBackground,
  } = useDropzone({
    onDrop: onDropBackground,
    accept: 'image/*',
    multiple: false,
  });

  // Dropzone pour la galerie (plusieurs fichiers)
  const onDropGallery = useCallback((acceptedFiles) => {
    if (acceptedFiles.length > 0) {
      setGallery((prevFiles) => [...prevFiles, ...acceptedFiles]);
    }
  }, []);

  const {
    getRootProps: getRootPropsGallery,
    getInputProps: getInputPropsGallery,
    isDragActive: isDragActiveGallery,
  } = useDropzone({
    onDrop: onDropGallery,
    accept: 'image/*',
    multiple: true,
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    const formData = {
      name,
      logo,
      background,
      description,
      members: members.split(',').map((m) => m.trim()),
      gallery,
      contacts,
    };
    console.log(formData);
    // Ici, vous pouvez envoyer formData à votre backend
  };

  return (
    <div className="form-container">
      <form onSubmit={handleSubmit} className="glass-form">
        <h2>Créer une Association</h2>
        <div className="form-grid">
          <div className="form-group">
            <label>Nom :</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>
          
          <div className="form-group">
            <label>Logo :</label>
            <div {...getRootPropsLogo()} className={`dropzone ${isDragActiveLogo ? 'active' : ''}`}>
              <input {...getInputPropsLogo()} />
              {logo ? (
                <p>{logo.name}</p>
              ) : isDragActiveLogo ? (
                <p>Déposez l'image du logo ici...</p>
              ) : (
                <p>Glissez-déposez l'image du logo ou cliquez pour sélectionner</p>
              )}
            </div>
          </div>

          <div className="form-group">
            <label>Arrière-plan :</label>
            <div {...getRootPropsBackground()} className={`dropzone ${isDragActiveBackground ? 'active' : ''}`}>
              <input {...getInputPropsBackground()} />
              {background ? (
                <p>{background.name}</p>
              ) : isDragActiveBackground ? (
                <p>Déposez l'image d'arrière-plan ici...</p>
              ) : (
                <p>Glissez-déposez l'image d'arrière-plan ou cliquez pour sélectionner</p>
              )}
            </div>
          </div>

          <div className="form-group">
            <label>Description :</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label>Membres (séparés par des virgules) :</label>
            <input
              type="text"
              value={members}
              onChange={(e) => setMembers(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label>Galerie de photos :</label>
            <div {...getRootPropsGallery()} className={`dropzone ${isDragActiveGallery ? 'active' : ''}`}>
              <input {...getInputPropsGallery()} />
              {gallery.length > 0 ? (
                <p>{gallery.length} fichier(s) sélectionné(s)</p>
              ) : isDragActiveGallery ? (
                <p>Déposez les images ici...</p>
              ) : (
                <p>Glissez-déposez les images ou cliquez pour sélectionner</p>
              )}
            </div>
          </div>

          <div className="form-group">
            <label>Contacts :</label>
            <input
              type="text"
              value={contacts}
              onChange={(e) => setContacts(e.target.value)}
            />
          </div>
        </div>
        <button type="submit">Créer l'association</button>
      </form>
    </div>
  );
};

export default CreateAssociationForm;
