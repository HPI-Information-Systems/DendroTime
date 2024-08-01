import React, { createContext, useRef } from "react";
import { useDimensions } from "../util";

export const WidthContext = createContext(0);

function WidthProvider({children}) {
  const ref = useRef(null);
  const { width, height } = useDimensions(ref);

  return (
    <div aria-hidden ref={ref}>
      <WidthContext.Provider value={width}>
        {children}
      </WidthContext.Provider>
    </div>
  )
}

export default WidthProvider;
