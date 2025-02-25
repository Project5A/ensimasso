import React, { useEffect, useState } from 'react';
import { useUser } from '../../contexts/UserContext';
import { FaUsers, FaBuilding, FaSpinner, FaExclamationTriangle } from 'react-icons/fa';

const Adhesion = () => {
  const { user } = useUser();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchMemberships = async () => {
      try {
        let response;
        if (user.role === 'GUEST') {
          response = await fetch(`http://localhost:8080/api/guests/${user.id}/adhesions`);
        } else if (user.role === 'ASSO') {
          response = await fetch(`http://localhost:8080/api/assos/${user.id}/adhesions`);
        }
  
        if (!response.ok) {
          throw new Error('Erreur lors de la récupération des adhésions');
        }
  
        const result = await response.json();
        setData(result);
      } catch (err) {
        console.error("Erreur dans le fetch :", err);
        setError(`Erreur : ${err.message}`);
      } finally {
        setLoading(false);
      }
    };
  
    fetchMemberships();
  }, [user]);

  if (loading) {
    return (
      <div className="flex justify-center items-center h-screen bg-gray-900 text-white">
        <FaSpinner className="animate-spin text-4xl mr-3" />
        Chargement...
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col justify-center items-center h-screen bg-gray-900 text-red-500">
        <FaExclamationTriangle className="text-4xl mb-3" />
        {error}
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <h1 className="text-3xl font-bold text-center mb-6">Adhésions</h1>
      
      {user.role === 'GUEST' ? (
        <div>
          <h2 className="text-xl font-semibold mb-4">Associations auxquelles vous êtes adhérent :</h2>
          {data.length === 0 ? (
            <p className="text-gray-400">Vous n'êtes adhérent à aucune association.</p>
          ) : (
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
              {data.map((asso) => (
                <div key={asso.id} className="bg-gray-800 p-6 rounded-lg shadow-md hover:scale-105 transition-transform duration-300">
                  <div className="flex items-center mb-3">
                    <FaBuilding className="text-yellow-500 text-2xl mr-3" />
                    <h3 className="text-lg font-semibold">{asso.name}</h3>
                  </div>
                  <p className="text-gray-400">Description : {asso.description || "Aucune description"}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <div>
          <h2 className="text-xl font-semibold mb-4">Membres adhérents :</h2>
          {data.length === 0 ? (
            <p className="text-gray-400">Aucun membre adhérent.</p>
          ) : (
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
              {data.map((guest) => (
                <div key={guest.id} className="bg-gray-800 p-6 rounded-lg shadow-md hover:scale-105 transition-transform duration-300">
                  <div className="flex items-center mb-3">
                    <FaUsers className="text-green-500 text-2xl mr-3" />
                    <h3 className="text-lg font-semibold">{guest.name}</h3>
                  </div>
                  <p className="text-gray-400">Statut : {guest.statut}</p>
                  <p className="text-gray-400">Promo : {guest.promo || "N/A"}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default Adhesion;
