import React from 'react';
import './Aboutsection.css';

const AboutSection = () => {
    return (
        <div className="about-section-container">
            <p className="about-section-text">
                Nous sommes un groupe d'étudiants en 5ème année à l'ENSIM, dans la filière IPS. 
                Ce site web est le fruit de notre travail collectif. Voici les membres de notre équipe :
            </p>
            <div className="team">
                <div className="team-member">
                    <h2>Taha Bouiber</h2>
                    <p>Étudiant passionné par le développement web.</p>
                </div>
                <div className="team-member">
                    <h2>Achraf Elkalchy</h2>
                    <p>Fasciné par les systèmes embarqués et les objets connectés.</p>
                </div>
                <div className="team-member">
                    <h2>Yahya Yacoubi</h2>
                    <p>Intéressé par l'informatique industrielle et les solutions innovantes.</p>
                </div>
            </div>
        </div>
    );
};

export { AboutSection };
