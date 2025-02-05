import React, { useState } from 'react';
import './CreateAssociationForm.css';

const CreateAssociationForm = () => {
  const [name, setName] = useState('');
  const [logo, setLogo] = useState(null);
  const [background, setBackground] = useState(null);
  const [description, setDescription] = useState('');
  const [members, setMembers] = useState('');
  const [gallery, setGallery] = useState([]);
  const [contacts, setContacts] = useState('');

  const handleLogoChange = (e) => {
    setLogo(e.target.files[0]);
  };

  const handleBackgroundChange = (e) => {
    setBackground(e.target.files[0]);
  };

  const handleGalleryChange = (e) => {
    setGallery([...e.target.files]);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // Traitez les données du formulaire ici
    const formData = {
      name,
      logo,
      background,
      description,
      members: members.split(',').map((member) => member.trim()),
      gallery,
      contacts,
    };
    console.log(formData);
    // Vous pouvez envoyer formData à votre backend ou effectuer d'autres actions nécessaires
  };

  return (
    <form onSubmit={handleSubmit}>
      <div>
        <label>Nom :</label>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
      </div>
      <div>
        <label>Logo :</label>
        <input
          type="file"
          accept="image/*"
          onChange={handleLogoChange}
        />
      </div>
      <div>
        <label>Arrière-plan :</label>
        <input
          type="file"
          accept="image/*"
          onChange={handleBackgroundChange}
        />
      </div>
      <div>
        <label>Description :</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          required
        />
      </div>
      <div>
        <label>Membres (séparés par des virgules) :</label>
        <input
          type="text"
          value={members}
          onChange={(e) => setMembers(e.target.value)}
        />
      </div>
      <div>
        <label>Galerie de photos :</label>
        <input
          type="file"
          accept="image/*"
          multiple
          onChange={handleGalleryChange}
        />
      </div>
      <div>
        <label>Contacts :</label>
        <input
          type="text"
          value={contacts}
          onChange={(e) => setContacts(e.target.value)}
        />
      </div>
      <button type="submit">Créer l'association</button>
    </form>
  );
};

export default CreateAssociationForm;
