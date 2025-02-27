import React from 'react';
import './Aboutsection.css';

const AboutSection = () => {

    const team = [
        {
            avatar: "https://media.licdn.com/dms/image/v2/D5603AQG3x6V7Kf1eZw/profile-displayphoto-shrink_400_400/profile-displayphoto-shrink_400_400/0/1732217225309?e=1746057600&v=beta&t=Q-7zXoJbXTDHLe1SZ3eJI15WZbOPUEdeEColwqvHiqE",
            name: "ELYACOUBI YAHYA",

            title: "Développeur full stack"
        },
        {
            avatar: "https://achrafwe.github.io/Portfo/assets/me.png",
            name: "EL KALCHY ACHRAF",
            title: "Développeur full stack"
        },
        {
            avatar: "https://tahabouiber.github.io/portfolio/profile.png",
            name: "BOUIBER TAHA",
            title: "Développeur full stack"
        },
    ]

    return (
        <section className="py-32">
            <div className="max-w-screen-xl mx-auto px-4 md:px-8">
                <div className="max-w-xl mx-auto sm:text-center">
                    <h3 className="text-white text-3xl font-semibold sm:text-4xl">
                    Notre équipe
                    </h3>
                    <p className="text-white mt-3">
                    Notre équipe de développement a conçu un site web innovant pour les associations de notre école, offrant une plateforme intuitive et dynamique qui facilite la communication, l’organisation et la mise en avant de leurs activités.
                    </p>
                </div>
                <div className="mt-12">
                    <ul className="grid gap-8 sm:grid-cols-2 md:grid-cols-3">
                        {
                            team.map((item, idx) => (
                                <li key={idx}>
                                    <div className="w-full h-60 sm:h-52 md:h-56">
                                        <img
                                            src={item.avatar}
                                            className="image"
                                            alt=""
                                        />
                                    </div>
                                    <div className="mt-4">
                                        <h4 className="text-lg text-white font-semibold">{item.name}</h4>
                                        <p className="text-white">{item.title}</p>
                                    </div>
                                </li>
                            ))
                        }
                    </ul>
                </div>
            </div>
        </section>
    )
};

export { AboutSection };
