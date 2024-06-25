import logo from "./logo.svg";

function Logo() {
  return (
    <div className="flex lg:flex-1">
      <a href="#" className="-m-1.5 p-1.5">
        <span className="sr-only">DendroTime</span>
        <img src={logo} className="h-10 w-auto" alt="DendroTime logo"/>
      </a>
    </div>
  )
}

export default Logo;
