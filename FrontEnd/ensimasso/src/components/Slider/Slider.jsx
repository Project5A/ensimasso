import React, { useEffect, useRef } from 'react';
import './Slider.css'; 

const Slider = () => {
  const sliderRef = useRef(null);
  const sliderListRef = useRef(null);
  const thumbnailRef = useRef(null);
  const nextBtnRef = useRef(null);
  const prevBtnRef = useRef(null);

  useEffect(() => {
    const thumbnailItems = thumbnailRef.current.querySelectorAll('.item');
    thumbnailRef.current.appendChild(thumbnailItems[0]);
  
    const moveSlider = (direction) => {
      const sliderItems = sliderListRef.current.querySelectorAll('.item');

      // Gérer les animations des éléments de slider
      sliderItems.forEach(item => {
        item.classList.remove('slide-in', 'slide-out', 'fade-out', 'fade-in');
      });

      if (direction === 'next') {
        sliderItems[0].classList.add('slide-out', 'fade-out');
        sliderItems[1].classList.add('slide-in', 'fade-in');

        setTimeout(() => {
          sliderListRef.current.appendChild(sliderItems[0]);
          sliderItems[0].classList.remove('slide-out', 'fade-out');
        }, 500);
      } else {
        sliderItems[sliderItems.length - 1].classList.add('slide-in', 'fade-in');
        sliderItems[sliderItems.length - 2].classList.add('slide-out', 'fade-out');

        setTimeout(() => {
          sliderListRef.current.prepend(sliderItems[sliderItems.length - 1]);
          sliderItems[sliderItems.length - 2].classList.remove('slide-out', 'fade-out');
        }, 500);
      }
    
      // Mettre à jour l'animation des vignettes
      if (direction === 'next') {
        thumbnailRef.current.appendChild(thumbnailItems[0]);
      } else {
        thumbnailRef.current.prepend(thumbnailItems[thumbnailItems.length - 1]);
      }
    };    
     
    nextBtnRef.current.onclick = () => moveSlider('next');
    prevBtnRef.current.onclick = () => moveSlider('prev');
  }, []);
  
  
  return (
    <div className="slider" ref={sliderRef}>
      <div className="list" ref={sliderListRef}>
        <div className="item">
          <img src="./assets/gala_bg.jpg" alt="" />
          <div className="content">
            <div className="title">Éclat et élégance, c'est notre spécialité !</div>
            <div className="type">GALA</div>
            <div className="description">
              GALA brille dans l'organisation d'événements mémorables, des soirées prestigieuses aux cérémonies de remise de diplômes. Nous mettons en lumière les moments inoubliables de votre parcours académique.
            </div>
            <div className="button">
              <button>Go →</button>
            </div>
          </div>
        </div>
        <div className="item">
          <img src="./assets/bde_bg.jpg" alt="" />
          <div className="content">
            <div className="title">Créateurs de souvenirs, moteurs d'intégration !</div>
            <div className="type">BDE</div>
            <div className="description">
              Le BDE est le cœur battant de la vie étudiante. Nous sommes là pour organiser des soirées et des événements inoubliables, mais aussi pour favoriser l'intégration et le bien-être de tous les étudiants de l'école.
            </div>
            <div className="button">
              <button>Go →</button>
            </div>
          </div>
        </div>
        <div className="item">
          <img src="./assets/bdlc_bg.jpg" alt="" />
          <div className="content">
            <div className="title">Découvrir, s'épanouir, s'amuser ensemble !</div>
            <div className="type">PLANT</div>
            <div className="description">
              Au BDLC, nous cultivons une atmosphère de diversité et de découverte. En gérant les différents clubs de l'ENSIM et en organisant une multitude d'activités, nous encourageons chacun à explorer ses passions, à s'épanouir et à tisser des liens durables au sein de notre communauté scolaire.
            </div>
            <div className="button">
              <button>Go →</button>
            </div>
          </div>
        </div>
        <div className="item">
          <img src="./assets/ensimersion_bg.png" alt="" />
          <div className="content">
            <div className="title">Saveurs, convivialité, et pause bien méritée !</div>
            <div className="type">Ensimersion</div>
            <div className="description">
              ENSIMERSION repousse les limites de la réalité en proposant des expériences de réalité virtuelle, augmentée et mixte. Que ce soit pour explorer des mondes fantastiques, vivre des aventures palpitantes ou pour des applications pratiques dans le domaine de l'éducation et de la formation, ENSIMERSION ouvre de nouveaux horizons technologiques et créatifs pour les étudiants de l'ENSIM.
            </div>
            <div className="button">
              <button>Go →</button>
            </div>
          </div>
        </div>
      </div>
      <div className="thumbnail" ref={thumbnailRef}>
        <div className="item">
          <img src="./assets/bde_logo.jpg" alt="" />
        </div>
        <div className="item">
          <img src="./assets/bdlc_logo.png" alt="" />
        </div>
        <div className="item">
          <img src="./assets/ensimersion_logo.png" alt="" />
        </div>
        <div className="item">
          <img src="./assets/gala_logo.jpg" alt="" />
        </div>
      </div>
      <div className="nextPrevArrows">
        <button className="prev" ref={prevBtnRef}> &lt; </button>
        <button className="next" ref={nextBtnRef}> &gt; </button>
      </div>
    </div>
  );
};

export {Slider};
