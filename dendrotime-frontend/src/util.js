import clsx from "clsx";
import {twMerge} from "tailwind-merge";
import { useMemo, useSyncExternalStore } from "react"

/**
 * A utility for constructing className strings without style conflicts. Use in the following:
 *
 * @example
 * cx(
 *   'table-row',
 *   !isShowArray ? 'absolute opacity-0' : 'relative',
 *   lineProps.className
 * )
 *
 * @param args any objects or strings
 * @returns {string}
 */
function cx(...args) {
  return twMerge(clsx(...args))
}

function subscribe(callback) {
  window.addEventListener("resize", callback)
  return () => {
    window.removeEventListener("resize", callback)
  }
}

/**
 * A utility for obtaining the dimensions of a DOM element.
 *
 * @example
 *  function MyComponent() {
 *    const ref = useRef(null)
 *    const {width, height} = useDimensions(ref)
 *    return <div ref={ref}>
 *      The dimensions of this div is {width} x {height}
 *    </div>
 *  }
 * @see https://stackoverflow.com/a/75101934/5384846
 * @param ref React ref object to the DOM element
 * @returns {width, height}
 */
function useDimensions(ref) {
  const dimensions = useSyncExternalStore(
    subscribe,
    () => JSON.stringify({
      width: ref.current?.offsetWidth ?? 0, // 0 is default width
      height: ref.current?.offsetHeight ?? 0, // 0 is default height
    })
  );
  return useMemo(() => JSON.parse(dimensions), [dimensions]);
}

export { useDimensions, cx };
