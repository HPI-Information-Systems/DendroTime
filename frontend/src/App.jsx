import React from "react";
import ClusteringPage from "./pages/ClusteringPage";
import "react-toastify/dist/ReactToastify.min.css";
import DendoTest from "./pages/DendoTest";
import {
  createBrowserRouter,
  RouterProvider,
} from "react-router-dom";
import ErrorPage from "./pages/ErrorPage";
import BarExample from "./pages/BarExample";
import RootLayout from "./pages/RootLayout";

const router = createBrowserRouter([
  {
    path: "/",
    element: <RootLayout />,
    errorElement: <ErrorPage />,
    children: [
      {errorElement: <ErrorPage />,
        children: [
          {
            index: true,
            element: <ClusteringPage/>
          },
          {
            path: "dendro-test",
            element: <DendoTest />
          },
          {
            path: "bar-example",
            element: <BarExample />
          }
        ]
      }
    ]
  },
]);

function App() {
  return <RouterProvider router={router} />;
}

export default App;
