import React, { useEffect, useRef } from 'react';
import './ShootingStars.css'; // Create this CSS file for styling

const ShootingStars = () => {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');

    // Function to set canvas size to full viewport
    const setCanvasSize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };

    // Initialize canvas size
    setCanvasSize();

    const stars = [];
    const numStars = 100;

    // Function to create stars
    const createStars = () => {
      stars.length = 0; // Clear existing stars
      for (let i = 0; i < numStars; i++) {
        stars.push({
          x: Math.random() * canvas.width, // Random horizontal position
          y: Math.random() * canvas.height, // Random vertical position
          size: Math.random() * 2, // Random size
          speed: Math.random() * 0.5 + 0.1, // Random speed
        });
      }
    };

    // Create initial stars
    createStars();

    const drawStars = () => {
      // Clear the canvas with a black background
      ctx.fillStyle = 'black';
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      // Draw stars
      ctx.fillStyle = 'white';
      stars.forEach(star => {
        ctx.beginPath();
        ctx.arc(star.x, star.y, star.size, 0, Math.PI * 2);
        ctx.fill();

        // Move the star downward
        star.y += star.speed;

        // Reset the star to the top if it goes off the screen
        if (star.y > canvas.height) {
          star.y = 0;
          star.x = Math.random() * canvas.width; // Randomize horizontal position
        }
      });

      // Loop the animation
      requestAnimationFrame(drawStars);
    };

    drawStars();

    // Handle window resize and zoom
    const handleResize = () => {
      setCanvasSize(); // Update canvas size
      createStars(); // Recreate stars based on new canvas size
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return <canvas ref={canvasRef} className="shooting-stars-canvas"></canvas>;
};

export default ShootingStars;