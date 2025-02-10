import React from 'react';
import ShootingStars from '../components/Background/ShootingStars';
import {AboutSection} from '../components/AboutSection/Aboutsection';
import '../components/CreateAssoForm/CreateAssociationForm';
import CreateAssociationForm from '../components/CreateAssoForm/CreateAssociationForm';

const About = () => {
  return (
    <div>
      <ShootingStars />
      <AboutSection />
      {/* <CreateAssociationForm /> */}
    </div>
  );
};

export default About;