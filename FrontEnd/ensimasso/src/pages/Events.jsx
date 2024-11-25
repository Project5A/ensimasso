import React from "react";
import TimeLine from "../components/Event/TimeLine";
import eventsBg from "../assets/events_bg.jpg";

function Events() {
  return (
    <div
      style={{
        backgroundImage: `url(${eventsBg})`,
        backgroundSize: "cover",
        backgroundPosition: "center",
        minHeight: "100vh",
      }}
    >
      <TimeLine />
    </div>
  );
}

export default Events;
