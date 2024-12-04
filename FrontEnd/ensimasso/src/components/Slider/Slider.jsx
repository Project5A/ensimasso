import React, { useEffect, useRef, useState } from 'react';
import './Slider.css';

const Slider = () => {
  const [slides, setSlides] = useState([]);
  const sliderRef = useRef(null);
  const sliderListRef = useRef(null);
  const thumbnailRef = useRef(null);
  const nextBtnRef = useRef(null);
  const prevBtnRef = useRef(null);

  useEffect(() => {
    // Simulate fetching data from a database
    const fetchSlides = async () => {
      const data = [
        {
          bg: './assets/gala_bg.jpg',
          title: "Éclat et élégance, c'est notre spécialité !",
          type: 'GALA',
          description:
            "GALA brille dans l'organisation d'événements mémorables, des soirées prestigieuses aux cérémonies de remise de diplômes. Nous mettons en lumière les moments inoubliables de votre parcours académique.",
          logo: './assets/gala_logo.jpg',
        },
        {
          bg: './assets/bde_bg.jpg',
          title: 'Créateurs de souvenirs, moteurs d\'intégration !',
          type: 'BDE',
          description:
            "Le BDE est le cœur battant de la vie étudiante. Nous sommes là pour organiser des soirées et des événements inoubliables, mais aussi pour favoriser l'intégration et le bien-être de tous les étudiants de l'école.",
          logo: './assets/bde_logo.jpg',
        },
        {
          bg: './assets/bdlc_bg.jpg',
          title: 'Découvrir, s’épanouir, s’amuser ensemble !',
          type: 'BDLC',
          description:
            "Au BDLC, nous cultivons une atmosphère de diversité et de découverte. En gérant les différents clubs de l'ENSIM et en organisant une multitude d'activités, nous encourageons chacun à explorer ses passions, à s’épanouir et à tisser des liens durables au sein de notre communauté scolaire.",
          logo: './assets/bdlc_logo.png',
        },
        {
          bg: './assets/ensimersion_bg.png',
          title: 'Saveurs, convivialité, et pause bien méritée !',
          type: 'Ensimersion',
          description:
            "ENSIMERSION repousse les limites de la réalité en proposant des expériences de réalité virtuelle, augmentée et mixte. Que ce soit pour explorer des mondes fantastiques, vivre des aventures palpitantes ou pour des applications pratiques dans le domaine de l’éducation et de la formation, ENSIMERSION ouvre de nouveaux horizons technologiques et créatifs pour les étudiants de l’ENSIM.",
          logo: './assets/ensimersion_logo.png',
        },
      ];
      setSlides(data);
    };

    fetchSlides();
  }, []);

  useEffect(() => {
    const moveSlider = (direction) => {
        const sliderItems = sliderListRef.current.querySelectorAll('.item');
        const thumbnailItems = thumbnailRef.current.querySelectorAll('.item');
  
        sliderItems.forEach((item) =>
            item.classList.remove('slide-in', 'slide-out', 'slide-in-reverse')
        );
        thumbnailItems.forEach((item) =>
            item.classList.remove('move-next', 'move-prev', 'active')
        );
  
        if (direction === 'next') {
            sliderItems[0].classList.add('slide-out');
            sliderItems[1].classList.add('slide-in');
  
            thumbnailItems[0].classList.add('move-next');
            thumbnailItems[1].classList.add('active');
  
            setTimeout(() => {
                sliderListRef.current.appendChild(sliderItems[0]);
                thumbnailRef.current.appendChild(thumbnailItems[0]);
            }, 500);
        } else {
            sliderItems[sliderItems.length - 1].classList.add('slide-in-reverse');
            sliderItems[0].classList.add('slide-out-reverse');
  
            thumbnailItems[thumbnailItems.length - 1].classList.add('move-prev');
            thumbnailItems[thumbnailItems.length - 2].classList.add('active');
  
            setTimeout(() => {
                sliderListRef.current.prepend(sliderItems[sliderItems.length - 1]);
                thumbnailRef.current.prepend(thumbnailItems[thumbnailItems.length - 1]);
            }, 500);
        }
    };  
    nextBtnRef.current.onclick = () => moveSlider('next');
    prevBtnRef.current.onclick = () => moveSlider('prev');
}, [slides]);


  return (
    <div className="slider" ref={sliderRef}>
      <div className="list" ref={sliderListRef}>
        {slides.map((slide, index) => (
          <div className="item" key={index}>
            <img src={slide.bg} alt="" />
            <div className="content">
              <div className="title">{slide.title}</div>
              <div className="type">{slide.type}</div>
              <div className="description">{slide.description}</div>
              <div className="button">
                <button>Go →</button>
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="thumbnail" ref={thumbnailRef}>
        {slides.map((slide, index) => (
          <div className="item" key={index}>
            <img src={slide.logo} alt="" />
          </div>
        ))}
      </div>
      <div className="nextPrevArrows">
        <button className="prev" ref={prevBtnRef}> ← </button>
        <button className="next" ref={nextBtnRef}> → </button>
      </div>
    </div>
  );
};

export {Slider};
