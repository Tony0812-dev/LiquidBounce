import { Account } from "~/utils/api";
import { ListItem } from "..";

type AccountEntryProps = {
  account: Account;
};

export default function AccountEntry({ account }: AccountEntryProps) {
  return (
    <ListItem>
      {/* Account Head Wrapper */}
      <div className="relative h-[68px] w-[68px]">
        {/* Account Head */}
        <img
          src={account.faceUrl}
          alt="Account Head"
          className="rounded-full"
        />
      </div>

      {/* Metadata */}
      <div className="flex flex-col space-y-1">
        {/* Username Wrapper */}
        <div className="text-white text-xl font-semibold uppercase">
          {account.username}
        </div>

        {/* E-mail */}
        <div className="text-white/50 text-xl font-semibold">
          example@placeholder.com
        </div>
      </div>
    </ListItem>
  );
}