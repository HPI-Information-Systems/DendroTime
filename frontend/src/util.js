import clsx from "clsx";
import {twMerge} from "tailwind-merge";

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
export function cx(...args) {
  return twMerge(clsx(...args))
}