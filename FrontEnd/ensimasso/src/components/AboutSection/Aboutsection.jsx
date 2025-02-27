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
        <section className="py-20 bg-gradient-to-br from-gray-900 to-blue-900">
            <div className="max-w-screen-xl mx-auto px-4 md:px-8">
                <div className="max-w-3xl mx-auto text-center mb-16">
                    <h3 className="text-4xl font-bold bg-gradient-to-r from-blue-400 to-blue-600 bg-clip-text text-transparent">
                        Notre équipe
                    </h3>
                    <p className="text-gray-300 mt-4 text-lg leading-relaxed">
                        Notre équipe de développement a conçu une plateforme innovante pour les associations de notre école,
                        offrant une solution intuitive et dynamique pour faciliter la communication et l'organisation des activités.
                    </p>
                </div>
                <div className="mt-12">
                    <ul className="grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
                        {team.map((member, idx) => (
                            <li 
                                key={idx}
                                className="group relative rounded-2xl transition-all duration-300 hover:-translate-y-2"
                            >
                                <div className="absolute inset-0 bg-gradient-to-b from-blue-500 to-blue-700 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
                                <div className="relative h-full bg-gray-800 rounded-2xl p-6">
                                    <div className="flex justify-center -mt-16">
                                        <div className="relative w-32 h-32">
                                            <img
                                                src={member.avatar}
                                                className="rounded-full w-full h-full object-cover border-4 border-blue-500 shadow-lg"
                                                alt={member.name}
                                            />
                                            <div className="absolute inset-0 rounded-full border-2 border-white/20"></div>
                                        </div>
                                    </div>
                                    <div className="mt-8 text-center">
                                        <h4 className="text-2xl font-bold text-white mb-2">{member.name}</h4>
                                        <p className="text-blue-400 font-medium">{member.title}</p>
                                        <div className="mt-4 flex justify-center space-x-4">
                                            {/* Add social icons here if needed */}
                                        </div>
                                    </div>
                                </div>
                            </li>
                        ))}
                    </ul>
                </div>
            </div>
        </section>
    )
};

export { AboutSection };