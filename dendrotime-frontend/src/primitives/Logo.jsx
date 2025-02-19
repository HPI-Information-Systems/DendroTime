import logo from './dendrotime-icon.webp';

function Logo() {
  return (
    <div className="flex lg:flex-1">
      <div className="flex items-center justify-center">
        <a href="#" className="-m-1.5 p-1.5">
          <span className="sr-only">DendroTime logo</span>
          <img src={logo} className="h-10 w-auto" alt="DendroTime logo"/>
        </a>
      </div>
      <div className="flex items-center justify-center mx-2">
        <span className="text-2xl font-bold text-gray-800">DendroTime</span>
      </div>
    </div>
  )
}

export default Logo;
