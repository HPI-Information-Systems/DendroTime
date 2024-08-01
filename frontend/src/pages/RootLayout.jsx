import React from "react";
import { Outlet } from "react-router-dom";
import Notifications from "../components/Notifications";
import Header from "../components/Header";

function RootLayout() {
  return (
    <div id={"App"} className="mx-auto max-w-screen-2xl">
      <Header/>
      <main className="lg:pl-36">
        <Outlet />
      </main>
      <Notifications/>
    </div>
  )
}

export default RootLayout;
